package dev.goodwy.rphone.auto

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.goodwy.rphone.R
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal sealed interface AndroidAutoScreenState {
    data object Loading : AndroidAutoScreenState
    data class Ready(val result: AndroidAutoCatalogResult) : AndroidAutoScreenState
    data object Failed : AndroidAutoScreenState
}

internal abstract class AndroidAutoCatalogScreen(
    carContext: CarContext,
    protected val source: AndroidAutoCatalogSource,
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadingJob: Job? = null
    protected var state: AndroidAutoScreenState = AndroidAutoScreenState.Loading
        private set

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) {
                    refresh(force = true)
                }

                override fun onStart(owner: LifecycleOwner) {
                    val refreshSupported = carContext
                        .getCarService(ConstraintManager::class.java)
                        .isAppDrivenRefreshEnabled
                    refresh(force = refreshSupported)
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    scope.cancel()
                }
            },
        )
    }

    private fun refresh(force: Boolean) {
        if (!force && state !is AndroidAutoScreenState.Loading) return
        if (loadingJob?.isActive == true) return
        loadingJob = scope.launch {
            state = if (state is AndroidAutoScreenState.Loading) {
                AndroidAutoScreenState.Loading
            } else {
                state
            }
            state = runCatching { source.load() }
                .fold(
                    onSuccess = AndroidAutoScreenState::Ready,
                    onFailure = { AndroidAutoScreenState.Failed },
                )
            invalidate()
        }
    }

    protected fun commonTemplate(
        title: String,
        headerAction: Action,
        content: (AndroidAutoCatalog) -> Template,
    ): Template = when (val currentState = state) {
        AndroidAutoScreenState.Loading -> ListTemplate.Builder()
            .setLoading(true)
            .setHeader(header(title, headerAction))
            .build()

        AndroidAutoScreenState.Failed -> MessageTemplate.Builder(
            carContext.getString(R.string.operation_failed),
        )
            .setHeader(header(title, headerAction))
            .build()

        is AndroidAutoScreenState.Ready -> when (currentState.result.permissionState) {
            AndroidAutoPermissionState.Granted -> content(currentState.result.catalog)
            AndroidAutoPermissionState.ContactsMissing -> permissionTemplate(
                title,
                headerAction,
                carContext.getString(R.string.contacts_permission_description),
            )
            AndroidAutoPermissionState.CallLogMissing -> permissionTemplate(
                title,
                headerAction,
                carContext.getString(R.string.call_history_permission),
            )
            AndroidAutoPermissionState.CallPermissionMissing -> permissionTemplate(
                title,
                headerAction,
                carContext.getString(R.string.android_auto_call_permission),
            )
        }
    }

    protected fun header(title: String, action: Action, endAction: Action? = null): Header =
        Header.Builder()
            .setStartHeaderAction(action)
            .setTitle(title)
            .apply { endAction?.let(::addEndHeaderAction) }
            .build()

    protected fun call(number: String) {
        carContext.startCarApp(
            Intent(Intent.ACTION_CALL).setData(Uri.fromParts("tel", number, null)),
        )
    }

    protected fun listLimit(): Int = carContext
        .getCarService(ConstraintManager::class.java)
        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        .coerceAtLeast(1)

    private fun permissionTemplate(title: String, action: Action, message: String): Template =
        MessageTemplate.Builder(message)
            .setHeader(header(title, action))
            .build()
}

internal class AndroidAutoHomeScreen(
    carContext: CarContext,
    source: AndroidAutoCatalogSource,
) : AndroidAutoCatalogScreen(carContext, source) {
    override fun onGetTemplate(): Template = commonTemplate(
        title = carContext.getString(R.string.app_name),
        headerAction = Action.APP_ICON,
    ) { catalog ->
        val limit = listLimit()
        val searchAction = Action.Builder()
            .setTitle(carContext.getString(R.string.search))
            .setIcon(
                androidx.car.app.model.CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_auto_search),
                ).build(),
            )
            .setOnClickListener {
                screenManager.push(AndroidAutoSearchScreen(carContext, catalog))
            }
            .build()
        val builder = ListTemplate.Builder()
            .setHeader(
                header(
                    carContext.getString(R.string.app_name),
                    Action.APP_ICON,
                    searchAction,
                ),
            )

        val keepContactsRow = catalog.contacts.isNotEmpty()
        var remaining = (limit - if (keepContactsRow) 1 else 0).coerceAtLeast(0)
        if (catalog.favorites.isNotEmpty() && remaining > 0) {
            val favoriteRows = catalog.favorites.take(minOf(4, remaining))
            builder.addSectionedList(
                SectionedItemList.create(
                    ItemList.Builder().apply {
                        favoriteRows.forEach { contact -> addItem(contact.callRow()) }
                    }.build(),
                    carContext.getString(R.string.favorites),
                ),
            )
            remaining -= favoriteRows.size
        }

        val recentLimit = remaining
        if (catalog.recents.isNotEmpty() && recentLimit > 0) {
            val recentRows = catalog.recents.take(recentLimit)
            builder.addSectionedList(
                SectionedItemList.create(
                    ItemList.Builder().apply {
                        recentRows.forEach { recent -> addItem(recent.callRow()) }
                    }.build(),
                    carContext.getString(R.string.recents),
                ),
            )
            remaining -= recentRows.size
        }

        if (keepContactsRow) {
            val contactsRow = Row.Builder()
                .setTitle(carContext.getString(R.string.contacts))
                .addText(carContext.getString(R.string.android_auto_browse_contacts))
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(AndroidAutoContactsScreen(carContext, source, page = 0))
                }
                .build()
            builder.addSectionedList(
                SectionedItemList.create(
                    ItemList.Builder().addItem(contactsRow).build(),
                    carContext.getString(R.string.contacts),
                ),
            )
        }

        if (remaining == limit) {
            builder.setSingleList(
                ItemList.Builder()
                    .setNoItemsMessage(carContext.getString(R.string.no_call_history_found))
                    .build(),
            )
        }
        builder.build()
    }

    private fun AndroidAutoContact.callRow(): Row = Row.Builder()
        .setTitle(displayName)
        .addText(number)
        .setOnClickListener { call(number) }
        .build()

    private fun AndroidAutoRecent.callRow(): Row = Row.Builder()
        .setTitle(displayName)
        .addText(number)
        .addText(
            DateUtils.getRelativeTimeSpanString(
                date,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ),
        )
        .setOnClickListener { call(number) }
        .build()
}

private class AndroidAutoSearchScreen(
    carContext: CarContext,
    private val catalog: AndroidAutoCatalog,
) : Screen(carContext) {
    private var query: String = ""
    private val callback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            query = searchText
            invalidate()
        }

        override fun onSearchSubmitted(searchText: String) {
            query = searchText
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val normalizedQuery = SearchTextNormalizer.normalize(query.trim(), foldDiacritics = true)
        val normalizedPhoneQuery = SearchTextNormalizer.normalizePhone(query)
        val results = if (normalizedQuery.isBlank() && normalizedPhoneQuery.isBlank()) {
            catalog.favorites.ifEmpty { catalog.contacts }
        } else {
            catalog.contacts.filter { contact ->
                normalizedQuery.isNotBlank() &&
                    SearchTextNormalizer.normalize(contact.displayName, foldDiacritics = true)
                        .contains(normalizedQuery) ||
                    normalizedPhoneQuery.isNotBlank() &&
                    SearchTextNormalizer.normalizePhone(contact.number).contains(normalizedPhoneQuery)
            }
        }
        val limit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
            .coerceAtLeast(1)
        val list = ItemList.Builder()
        results.take(limit).forEach { contact ->
            list.addItem(
                Row.Builder()
                    .setTitle(contact.displayName)
                    .addText(contact.number)
                    .setOnClickListener {
                        carContext.startCarApp(
                            Intent(Intent.ACTION_CALL).setData(
                                Uri.fromParts("tel", contact.number, null),
                            ),
                        )
                    }
                    .build(),
            )
        }
        if (results.isEmpty()) {
            list.setNoItemsMessage(carContext.getString(R.string.no_contacts_found))
        }

        return SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.search_contacts))
            .setInitialSearchText(query)
            .setShowKeyboardByDefault(true)
            .setItemList(list.build())
            .build()
    }
}

private class AndroidAutoContactsScreen(
    carContext: CarContext,
    source: AndroidAutoCatalogSource,
    private val page: Int,
) : AndroidAutoCatalogScreen(carContext, source) {
    override fun onGetTemplate(): Template = commonTemplate(
        title = carContext.getString(R.string.contacts),
        headerAction = Action.BACK,
    ) { catalog ->
        val limit = listLimit()
        val pageSize = (limit - 1).coerceAtLeast(1)
        val lastPage = ((catalog.contacts.size - 1).coerceAtLeast(0) / pageSize)
        val safePage = page.coerceIn(0, lastPage)
        val from = safePage * pageSize
        val contacts = catalog.contacts.drop(from).take(pageSize)
        val hasNextPage = from + contacts.size < catalog.contacts.size

        val list = ItemList.Builder()
        contacts.forEach { contact ->
            list.addItem(
                Row.Builder()
                    .setTitle(contact.displayName)
                    .addText(contact.number)
                    .setOnClickListener { call(contact.number) }
                    .build(),
            )
        }
        if (hasNextPage) {
            list.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.next))
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(AndroidAutoContactsScreen(carContext, source, safePage + 1))
                    }
                    .build(),
            )
        }
        if (contacts.isEmpty()) {
            list.setNoItemsMessage(carContext.getString(R.string.no_contacts_found))
        }

        ListTemplate.Builder()
            .setHeader(header(carContext.getString(R.string.contacts), Action.BACK))
            .setSingleList(list.build())
            .build()
    }
}

package dev.goodwy.rphone.view.screen
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon

@Composable
fun EditPhoneField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDelete: (() -> Unit)? = null,
    onLabelChange: ((String, Int?) -> Unit)? = null
) {
    val phoneTypes = listOf(
        Phone.TYPE_MOBILE to stringResource(R.string.mobile),
        Phone.TYPE_WORK to stringResource(R.string.work),
        Phone.TYPE_HOME to stringResource(R.string.home),
        Phone.TYPE_MAIN to stringResource(R.string.main_number),
//        Phone.TYPE_FAX_WORK to stringResource(R.string.work_fax),
//        Phone.TYPE_FAX_HOME to stringResource(R.string.home_fax),
//        Phone.TYPE_PAGER to stringResource(R.string.pager),
        Phone.TYPE_OTHER to stringResource(R.string.other)
    ).distinctBy { it.first }

    EditField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        icon = DotDialerHandsetIcon,
        onDelete = onDelete,
        onLabelChange = onLabelChange,
        availableLabels = phoneTypes
    )
}

@Composable
fun EditEmailField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDelete: (() -> Unit)? = null,
    onLabelChange: ((String, Int?) -> Unit)? = null
) {
    val emailTypes = listOf(
        Email.TYPE_HOME to stringResource(R.string.home),
        Email.TYPE_WORK to stringResource(R.string.work),
        Email.TYPE_MOBILE to stringResource(R.string.mobile),
        Email.TYPE_OTHER to stringResource(R.string.other)
    )

    EditField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        icon = Icons.Default.Email,
        onDelete = onDelete,
        onLabelChange = onLabelChange,
        availableLabels = emailTypes
    )
}

@Composable
fun EditAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDelete: (() -> Unit)? = null,
    onLabelChange: ((String, Int?) -> Unit)? = null
) {
    val addressTypes = listOf(
        StructuredPostal.TYPE_HOME to stringResource(R.string.home),
        StructuredPostal.TYPE_WORK to stringResource(R.string.work),
        StructuredPostal.TYPE_OTHER to stringResource(R.string.other)
    )

    EditField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        icon = Icons.Default.LocationOn,
        onDelete = onDelete,
        onLabelChange = onLabelChange,
        availableLabels = addressTypes
    )
}

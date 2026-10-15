package io.github.bootika.dotdialer.core.search

data class SearchSessionState(
    val query: String = "",
    val keyboardVisible: Boolean = false,
)

sealed interface SearchSessionAction {
    data class QueryChanged(val query: String) : SearchSessionAction
    data object CallInitiated : SearchSessionAction
}

object SearchSessionReducer {
    fun reduce(state: SearchSessionState, action: SearchSessionAction): SearchSessionState =
        when (action) {
            is SearchSessionAction.QueryChanged -> state.copy(query = action.query)
            SearchSessionAction.CallInitiated -> SearchSessionState(
                query = "",
                keyboardVisible = false,
            )
        }
}

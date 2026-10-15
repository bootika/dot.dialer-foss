package dev.goodwy.rphone.view.screen

import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.view.components.distinctAdjacentAvatarColorIndices

internal data class HomeAvatarColorPlan(
    val recentIndices: List<Int>,
    val recentIndexByNumber: Map<String, Int>,
    val contactIndices: List<Int>,
)

internal fun buildHomeAvatarColorPlan(
    recentIdentity: List<Pair<String, String>>,
    contactNames: List<String>,
): HomeAvatarColorPlan {
    val recentIndices = distinctAdjacentAvatarColorIndices(recentIdentity.map { (name, number) ->
        dev.goodwy.rphone.view.components.avatarIdentityColorSeed(name, number)
    })
    return HomeAvatarColorPlan(
        recentIndices = recentIndices,
        recentIndexByNumber = recentIdentity.mapIndexed { index, identity ->
            identity.second to recentIndices[index]
        }.toMap(),
        contactIndices = distinctAdjacentAvatarColorIndices(contactNames),
    )
}

internal fun DestinationsNavigator.navigateToNewContact(number: String, avatarColorIndex: Int?) {
    navigate(
        ContactEditScreenDestination(
            initialPhone = number,
            initialAvatarColorIndex = avatarColorIndex,
        )
    )
}

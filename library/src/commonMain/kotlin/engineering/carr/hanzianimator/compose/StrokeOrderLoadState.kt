package engineering.carr.hanzianimator.compose

import engineering.carr.hanzianimator.core.StrokeOrder

/** The async load state of a character's [StrokeOrder] for the opinionated composables. */
internal sealed interface StrokeOrderLoadState {
    data object Loading : StrokeOrderLoadState
    data object Missing : StrokeOrderLoadState
    data class Loaded(val order: StrokeOrder) : StrokeOrderLoadState
}

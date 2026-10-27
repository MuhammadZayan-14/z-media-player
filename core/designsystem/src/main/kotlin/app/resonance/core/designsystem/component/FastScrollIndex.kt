package app.resonance.core.designsystem.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** First list index for each initial letter; '#' groups digits and symbols. */
fun <T> alphabetSections(items: List<T>, offset: Int = 0, label: (T) -> String?): List<Pair<Char, Int>> {
    val sections = LinkedHashMap<Char, Int>()
    items.forEachIndexed { index, item ->
        val first = label(item)?.trim()?.firstOrNull()?.uppercaseChar()
        val key = if (first != null && first.isLetter()) first else '#'
        sections.putIfAbsent(key, index + offset)
    }
    return sections.entries.map { it.key to it.value }
}

/**
 * Letter strip for jumping through long alphabetical lists: tap or drag along it.
 * Hidden from accessibility services, which already get the list's own scroll actions.
 */
@Composable
fun FastScrollIndex(
    sections: List<Pair<Char, Int>>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    if (sections.size < MIN_SECTIONS) return
    val scope = rememberCoroutineScope()
    val currentSections by rememberUpdatedState(sections)
    var height by remember { mutableIntStateOf(1) }

    fun scrollTo(y: Float) {
        val list = currentSections
        val index = ((y / height) * list.size).toInt().coerceIn(0, list.lastIndex)
        scope.launch { listState.scrollToItem(list[index].second) }
    }

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .padding(vertical = 8.dp)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .clearAndSetSemantics { }
            .pointerInput(Unit) { detectTapGestures { scrollTo(it.y) } }
            .pointerInput(Unit) { detectVerticalDragGestures { change, _ -> scrollTo(change.position.y) } }
    ) {
        sections.forEach { (letter, _) ->
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private const val MIN_SECTIONS = 4

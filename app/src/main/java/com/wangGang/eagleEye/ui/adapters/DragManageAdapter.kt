import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.wangGang.eagleEye.ui.adapters.ItemTouchHelperAdapter

class DragManageAdapter(
    private val adapter: ItemTouchHelperAdapter
) : ItemTouchHelper.Callback() {

    // Allow drag but no swipe
    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // Forward the move event to the adapter
        adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    // Not used
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
}

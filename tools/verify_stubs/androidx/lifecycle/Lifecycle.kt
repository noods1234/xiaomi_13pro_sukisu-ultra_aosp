package androidx.lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
interface LifecycleOwner
val LifecycleOwner.lifecycleScope: CoroutineScope
    get() = CoroutineScope(SupervisorJob() + Dispatchers.Main)

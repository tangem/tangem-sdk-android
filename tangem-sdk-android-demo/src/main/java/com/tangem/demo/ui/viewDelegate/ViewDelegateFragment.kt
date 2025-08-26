package com.tangem.demo.ui.viewDelegate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tangem.Log
import com.tangem.SessionViewDelegate
import com.tangem.common.CompletionResult
import com.tangem.common.card.Card
import com.tangem.demo.DemoActivity
import com.tangem.demo.ui.BaseFragment
import com.tangem.tangem_demo.databinding.FgViewDelegateBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.coroutines.CoroutineContext

/**
[REDACTED_AUTHOR]
 */
class ViewDelegateFragment : BaseFragment() {

    private lateinit var delegate: SessionViewDelegate

    private val parentJob = Job()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val exceptionAsString: String = sw.toString()
        Log.error { exceptionAsString }
        throw throwable
    }
    private val coroutineContext: CoroutineContext = parentJob + Dispatchers.IO + exceptionHandler
    private val scope = CoroutineScope(coroutineContext)

    private var _binding: FgViewDelegateBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        delegate = (requireActivity() as DemoActivity).viewDelegate
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FgViewDelegateBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initActionsList()
    }

    private fun initActionsList() {
        val actionsList = mutableListOf<ViewDelegateAction>(
            TagConnectTagLostError(),
            SecurityDelay(),
            SecurityDelayPinFails(),
            WrongCard(),
            OnError(),
            RequestAccessCode(),
            RequestPasscode(),
            RequestUserCode(),
            RequestPinSetup(),
            SingleRequestAccessCode(false),
            SingleRequestAccessCode(true),
            ErrorsWithDifferentFormats(),
        )

        actionsList.forEach {
            it.inflateViews(binding.actionsContainer)
            it.init(delegate, scope)
        }
    }

    override fun handleCommandResult(result: CompletionResult<*>) {
    }

    override fun onCardChanged(card: Card?) {
    }
}
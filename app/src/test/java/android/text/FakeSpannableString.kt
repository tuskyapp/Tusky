package android.text

// Used for stubbing Android implementation without slow & buggy Robolectric things
@Suppress("unused")
class SpannableString(private val text: CharSequence) : Spannable {

    override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {
        throw NotImplementedError()
    }

    override fun <T : Any?> getSpans(start: Int, end: Int, type: Class<T>?): Array<T> {
        throw NotImplementedError()
    }

    override fun removeSpan(what: Any?) {
        throw NotImplementedError()
    }

    override fun toString(): String {
        return "FakeSpannableString[text=$text]"
    }

    override val length: Int
        get() = text.length


    override fun nextSpanTransition(start: Int, limit: Int, type: Class<*>?): Int {
        throw NotImplementedError()
    }

    override fun getSpanEnd(tag: Any?): Int {
        throw NotImplementedError()
    }

    override fun getSpanFlags(tag: Any?): Int {
        throw NotImplementedError()
    }

    override fun get(index: Int): Char {
        throw NotImplementedError()
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        throw NotImplementedError()
    }

    override fun getSpanStart(tag: Any?): Int {
        throw NotImplementedError()
    }
}
/*
 * @(#) HTMLPipeline.kt
 *
 * html-pipeline    Simple pipeline parser for HTML
 * Copyright (c) 2020 Peter Wall
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.pwall.html

import javax.xml.parsers.DocumentBuilderFactory

import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

import net.pwall.util.pipeline.AbstractIntAcceptor

/**
 * Simple parser for HTML, using the [`pipelines`](https://github.com/pwall567/pipelines) library.  This is not intended
 * to be a strict parser of HTML5; the main planned use is to help with "screen-scraping" HTML websites.  It may also
 * find use as a tool for testing HTML generation.
 *
 * @param   charsetCallback     a function that is called once the charset has been determined (this may be used to
 *                              modify the input process to switch charset in mid-stream)
 */
class HTMLPipeline(val charsetCallback: (String) -> Unit = {}) : AbstractIntAcceptor<Document>() {

    enum class State { TEXT, ANGLE_BRACKET_SEEN, EXCLAMATION_MARK_SEEN, DIRECTIVE, DOCTYPE,
            COMMENT1, COMMENT2, COMMENT3, COMMENT4, CDATA1, CDATA2, SCRIPT,
            ELEMENT, ELEMENT1, ELEMENT2, ELEMENT3, ATTRIBUTE, ATTRIBUTE1, QUOTED_ATTRIBUTE, UNQUOTED_ATTRIBUTE,
            END_ELEMENT, WORD, ERROR }

    private var state: State = State.TEXT

    private val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

    var docType: String? = null

    private val word = StringBuilder()
    private val textContent = StringBuilder()
    private var nextState: State = State.ERROR
    private var closeQuote = '"'.toInt()

    private var lineNumber = 1
    private var charOffset = 0

    private val elementStack = ArrayList<Element>()

    override fun acceptInt(ch: Int) {
        var reprocess = true
        while (reprocess) {
            reprocess = false
            when (state) {
                State.TEXT -> {
                    if (elementStack.isEmpty()) {
                        when {
                            ch == '<'.toInt() -> state = State.ANGLE_BRACKET_SEEN
                            HTML.isWhiteSpace(ch) -> {}
                            else -> error("Text outside elements")
                        }
                    }
                    else {
                        if (ch == '<'.toInt()) {
                            if (textContent.isNotEmpty())
                                elementStack.last().appendChild(document.createTextNode(textContent.toString()))
                            state = State.ANGLE_BRACKET_SEEN
                        }
                        else
                            textContent.append(ch.toChar())
                    }
                }
                State.ANGLE_BRACKET_SEEN -> {
                    when {
                        ch == '!'.toInt() -> state = State.EXCLAMATION_MARK_SEEN
                        ch == '/'.toInt() -> expectWord(State.END_ELEMENT)
                        ch.isAlpha() -> expectWord(ch, State.ELEMENT)
                        else -> error("Illegal character following <")
                    }
                }
                State.EXCLAMATION_MARK_SEEN -> {
                    when {
                        ch.isAlpha() -> expectWord(ch, State.DIRECTIVE)
                        ch == '-'.toInt() -> state = State.COMMENT1
                        ch == '['.toInt() -> expectWord(State.CDATA1)
                        else -> error("Illegal character following <!")
                    }
                }
                State.DIRECTIVE -> {
                    val directive = word.toString()
                    if (directive.toUpperCase() != "DOCTYPE")
                        error("Unrecognised directive - $directive")
                    if (docType != null)
                        error("Duplicate DOCTYPE")
                    textContent.setLength(0)
                    when {
                        HTML.isWhiteSpace(ch) -> state = State.DOCTYPE
                        ch == '>'.toInt() -> {
                            docType = ""
                            state = State.TEXT
                        }
                        else -> error("Illegal character in DOCTYPE")
                    }
                }
                State.DOCTYPE -> {
                    if (ch == '>'.toInt()) {
                        docType = HTML.trim(textContent.toString())
                        expectText()
                    }
                    else
                        textContent.append(ch.toChar())
                }
                State.COMMENT1 -> {
                    if (ch != '-'.toInt())
                        error("Illegal comment")
                    textContent.setLength(0)
                    state = State.COMMENT2
                }
                State.COMMENT2 -> {
                    if (ch == '-'.toInt())
                        state = State.COMMENT3
                    else
                        textContent.append(ch.toChar())
                }
                State.COMMENT3 -> {
                    state = when (ch) {
                        '-'.toInt() -> State.COMMENT4
                        else -> {
                            textContent.append('-').append(ch.toChar())
                            State.COMMENT2
                        }
                    }
                }
                State.COMMENT4 -> {
                    if (ch == '>'.toInt())
                        appendChildNode(document.createComment(textContent.toString()))
                    expectText()
                }
                State.CDATA1 -> {
                    if (word.toString() != "CDATA" || ch != '['.toInt())
                        error("Illegal directive")
                    textContent.setLength(0)
                    state = State.CDATA2
                }
                State.CDATA2 -> {
                    if (ch == '>'.toInt() && textContent.endsWithCloseCDATA()) {
                        textContent.setLength(textContent.length - 2)
                        elementStack.last().appendChild(document.createCDATASection(textContent.toString()))
                        expectText()
                    }
                    else
                        textContent.append(ch.toChar())
                }
                State.SCRIPT -> {
                    if (ch == '>'.toInt() && textContent.endsWithCloseScript()) {
                        if (textContent.isNotEmpty())
                            elementStack.last().appendChild(document.createTextNode(textContent.toString()))
                        elementStack.pop()
                        state = State.TEXT
                    }
                    else
                        textContent.append(ch.toChar())
                }
                State.ELEMENT -> {
                    val element = document.createElement(word.toString())
                    checkUnterminated(element)
                    appendChildNode(element)
                    elementStack.add(element)
                    state = State.ELEMENT1
                    reprocess = true
                }
                State.ELEMENT1 -> {
                    if (!HTML.isWhiteSpace(ch)) {
                        when {
                            ch.isAlpha() -> expectWord(ch, State.ATTRIBUTE)
                            ch == '>'.toInt() -> state = elementOpen()
                            ch == '/'.toInt() -> state = State.ELEMENT2
                            else -> error("Illegal character in element")
                        }
                    }
                }
                State.ELEMENT2 -> {
                    if (ch != '>'.toInt())
                        error("Illegal character in element")
                    elementStack.pop()
                    expectText()
                }
                State.ELEMENT3 -> {
                    state = when {
                        HTML.isWhiteSpace(ch) -> State.ELEMENT1
                        ch == '>'.toInt() -> elementOpen()
                        ch == '/'.toInt() -> State.ELEMENT2
                        else -> error("Illegal character following attribute")
                    }
                }
                State.ATTRIBUTE -> {
                    if (!HTML.isWhiteSpace(ch)) {
                        if (ch == '='.toInt())
                            state = State.ATTRIBUTE1
                        else {
                            elementStack.last().setAttribute(word.toString(), word.toString())
                            state = State.ELEMENT1
                            reprocess = true
                        }
                    }
                }
                State.ATTRIBUTE1 -> {
                    if (!HTML.isWhiteSpace(ch)) {
                        if (ch == '"'.toInt() || ch == '\''.toInt()) {
                            closeQuote = ch
                            textContent.setLength(0)
                            state = State.QUOTED_ATTRIBUTE
                        }
                        else {
                            textContent.setLength(0)
                            state = State.UNQUOTED_ATTRIBUTE
                            reprocess = true
                        }
                    }
                }
                State.QUOTED_ATTRIBUTE -> {
                    if (ch == closeQuote) {
                        elementStack.last().setAttribute(word.toString(), textContent.toString())
                        state = State.ELEMENT3
                    }
                    else
                        textContent.append(ch.toChar())
                }
                State.UNQUOTED_ATTRIBUTE -> {
                    if (HTML.isWhiteSpace(ch) || ch == '>'.toInt() || ch == '/'.toInt()) {
                        elementStack.last().setAttribute(word.toString(), textContent.toString())
                        state = State.ELEMENT3
                        reprocess = true
                    }
                    else
                        textContent.append(ch.toChar())
                }
                State.END_ELEMENT -> {
                    if (ch != '>'.toInt())
                        error("Closing tag error")
                    val tagName = word.toString()
                    while (elementStack.isNotEmpty()) {
                        val top = elementStack.last().tagName.toLowerCase()
                        elementStack.pop()
                        if (top == tagName)
                            break
                        if (!allowUnclosed.contains(top.toLowerCase()))
                            error("Tag not closed - $top")
                    }
                    expectText()
                }
                State.WORD -> {
                    if (ch.isAlpha() || ch.isDigit() || ch == '-'.toInt())
                        word.append(ch.toChar())
                    else {
                        state = nextState
                        reprocess = true
                    }
                }
                State.ERROR -> {}
            }
        }
        when (ch) {
            '\n'.toInt() -> {
                lineNumber++
                charOffset = 0
            }
            in ' '.toInt()..Character.MAX_CODE_POINT -> charOffset++
        }
    }

    override fun close() {
        if (state != State.TEXT && textContent.isEmpty())
            error("Document incomplete")
        super.close()
    }

    override fun getResult(): Document {
        return document
    }

    private fun checkUnterminated(element: Element) {
        if (elementStack.isNotEmpty()) {
            val thisTag = element.tagName.toLowerCase()
            var previousTag = elementStack.last().tagName.toLowerCase()
            if (previousTag == "p" && allowToCloseP.contains(thisTag))
                elementStack.pop()
            else
                when (thisTag) {
                    "body" -> {
                        if (previousTag == "head")
                            elementStack.pop()
                    }
                    "li" -> {
                        if (previousTag == "li")
                            elementStack.pop()
                    }
                    "dt", "dd" -> {
                        if (previousTag == "dt" || previousTag == "dd")
                            elementStack.pop()
                    }
                    "option" -> {
                        if (previousTag == "option")
                            elementStack.pop()
                    }
                    "optgroup" -> {
                        if (previousTag == "option") {
                            elementStack.pop()
                            if (elementStack.size >= 1)
                                previousTag = elementStack.last().tagName.toLowerCase()
                        }
                        if (previousTag == "optgroup")
                            elementStack.pop()
                    }
                    "td", "th" -> {
                        if (previousTag == "td" || previousTag == "th")
                            elementStack.pop()
                    }
                    "tr" -> {
                        if (previousTag == "td" || previousTag == "th") {
                            elementStack.pop()
                            if (elementStack.size >= 1)
                                previousTag = elementStack.last().tagName.toLowerCase()
                        }
                        if (previousTag == "tr")
                            elementStack.pop()
                    }
                    "tbody", "thead", "tfoot" -> {
                        if (previousTag == "td" || previousTag == "th") {
                            elementStack.pop()
                            if (elementStack.size >= 1)
                                previousTag = elementStack.last().tagName.toLowerCase()
                        }
                        if (previousTag == "tr") {
                            elementStack.pop()
                            if (elementStack.size >= 1)
                                previousTag = elementStack.last().tagName.toLowerCase()
                        }
                        if (previousTag == "tbody" || previousTag == "thead" || previousTag == "tfoot")
                            elementStack.pop()
                    }
                }
        }
    }

    private fun elementOpen(): State {
        val element = elementStack.last()
        val tagNameLower = element.tagName.toLowerCase()
        if (tagNameLower == "meta") {
            val charsetAtt = element.findAttribute("charset")
            if (charsetAtt !== null)
                charsetCallback(charsetAtt)
            else {
                if (element.findAttribute("http-equiv")?.toLowerCase() == "content-type") {
                    element.findAttribute("content")?.split(';')?.forEach {
                        val tokens = it.split('=', limit = 2)
                        if (tokens.size == 2 && HTML.trim(tokens[0]).toLowerCase() == "charset")
                            charsetCallback(HTML.trim(tokens[1]))
                    }
                }
            }
        }
        textContent.setLength(0)
        return when (tagNameLower) {
            "script", "style" -> State.SCRIPT
            else -> {
                if (HTML.elementsWithoutChildren.contains(tagNameLower))
                    elementStack.pop()
                State.TEXT
            }
        }
    }

    private fun expectText() {
        textContent.setLength(0)
        state = State.TEXT
    }

    private fun expectWord(ch: Int, nextState: State) {
        expectWord(nextState)
        word.append(ch.toChar())
    }

    private fun expectWord(nextState: State) {
        word.setLength(0)
        this.nextState = nextState
        state = State.WORD
    }

    private fun appendChildNode(child: Node) {
        val node = if (elementStack.isEmpty()) document else elementStack.last()
        node.appendChild(child)
    }

    private fun StringBuilder.endsWithCloseCDATA() =
            length >= 2 && this[length - 1] == ']' && this[length - 2] == ']'

    private fun StringBuilder.endsWithCloseScript(): Boolean {
        val tagName = elementStack.last().tagName
        val len = tagName.length
        if (length >= len + 2 && this[length - len - 2] == '<' && this[length - len - 1] == '/' &&
                subSequence(length - len, length).toString() == tagName) {
            setLength(length - len - 2)
            return true
        }
        return false
    }

    private fun error(message: String): Nothing {
        state = State.ERROR
        throw HTMLPipelineException(lineNumber, charOffset, message)
    }

    companion object {

        val allowUnclosed = listOf("td", "th", "tr", "thead", "tbody", "tfoot", "li", "dt", "dd", "p", "option",
                "optgroup")

        val allowToCloseP = listOf("address", "article", "aside", "blockquote", "details", "div", "dl", "fieldset",
                "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup", "hr",
                "main", "menu", "nav", "ol", "p", "pre", "section", "table", "ul")

        private fun <T> MutableList<T>.pop() = removeAt(size - 1)

        private fun Int.isAlpha() = this in 'A'.toInt()..'Z'.toInt() || this in 'a'.toInt()..'z'.toInt()

        private fun Int.isDigit() = this in '0'.toInt()..'9'.toInt()

        private fun Element.findAttribute(name: String): String? {
            val attributes = this.attributes
            for (i in 0 until attributes.length) {
                val attribute: Attr = attributes.item(i) as Attr
                if (attribute.name.toLowerCase() == name)
                    return attribute.value
            }
            return null
        }

    }

}

/*
 * @(#) HTMLPipelineTest.kt
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

import kotlin.test.Test
import kotlin.test.expect
import kotlin.test.fail

import java.io.File

import org.w3c.dom.CDATASection
import org.w3c.dom.Comment
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text

import net.pwall.dom.children
import net.pwall.dom.childrenByType
import net.pwall.dom.findElementByTagName
import net.pwall.util.pipeline.DecoderFactory

class HTMLPipelineTest {

    @Test fun `should parse simple html`() {
        File("src/test/resources/simple_html.html").inputStream().use {
            val html = HTMLPipeline()
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, html).apply {
                accept(it)
            }
            expect("html") { html.docType }
            val document = htmlPipeline.result
            val element = document.documentElement
            expect("html") { element.tagName }
            expect("simple") { element.findElementByTagName("title")?.textContent }
            expect("text") { element.findElementByTagName("p")?.textContent }
//            document.displayChildNodes()
        }
    }

    @Test fun `should parse simple html with no doctype`() {
        File("src/test/resources/simple_html_with_no_doctype.html").inputStream().use {
            val html = HTMLPipeline()
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, html).apply {
                accept(it)
            }
            expect(null) { html.docType }
            val document = htmlPipeline.result
            val element = document.documentElement
            expect("html") { element.tagName }
            expect("simple") { element.findElementByTagName("title")?.textContent }
            expect("text") { element.findElementByTagName("p")?.textContent }
        }
    }

    @Test fun `should parse simple html with attributes`() {
        File("src/test/resources/simple_html_with_attrs.html").inputStream().use {
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline()).apply {
                accept(it)
            }
            val document = htmlPipeline.result
            val element = document.documentElement
            val p = element.findElementByTagName("p") ?: fail()
            expect("123") { p.getAttribute("id") }
            expect("class1") { p.getAttribute("class") }
        }
    }

    @Test fun `should parse html with element without children`() {
        File("src/test/resources/html_with_element_without_children.html").inputStream().use {
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline()).apply {
                accept(it)
            }
            val document = htmlPipeline.result
            val body = document.documentElement.findElementByTagName("body") ?: fail()
            val children = body.childrenByType<Element>()
            expect("p") { children[0].tagName }
            expect("br") { children[1].tagName }
        }
    }

    @Test fun `should parse html with comments`() {
        File("src/test/resources/html_with_comments.html").inputStream().use {
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline()).apply {
                accept(it)
            }
            val document = htmlPipeline.result
            val children = document.documentElement.findElementByTagName("head")?.children() ?: fail()
            expect(5) { children.size }
            expect(true) { children[0].isAllWhiteSpace() }
            expect(true) { children[1].isComment(" comment ") }
            expect(true) { children[2].isAllWhiteSpace() }
            expect(true) { children[3].let { it is Element && it.tagName == "title" } }
            expect(true) { children[4].isAllWhiteSpace() }
        }
    }

    @Test fun `should parse html with comment including hyphen`() {
        File("src/test/resources/html_with_comment_including_hyphen.html").inputStream().use {
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline()).apply {
                accept(it)
            }
            val document = htmlPipeline.result
            val children = document.documentElement.findElementByTagName("head")?.children() ?: fail()
            expect(5) { children.size }
            expect(true) { children[1].isComment(" comment - including hyphen ") }
        }
    }

    @Test fun `should parse html with comments before and after document element`() {
        File("src/test/resources/html_with_multiple_comments.html").inputStream().use {
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline()).apply {
                accept(it)
            }
            val document = htmlPipeline.result
            val children = document.children()
            expect(3) { children.size }
            expect(true) { children[0].isComment(" comment before start ") }
            expect(true) { children[1] === document.documentElement }
            expect(true) { children[2].isComment(" comment after end ") }
        }
    }

    @Test fun `should parse html with meta tag`() {
        File("src/test/resources/html_with_meta_tag.html").inputStream().use {
            var charset: String? = null
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline { charset = it }).apply {
                accept(it)
            }
            val document = htmlPipeline.result
            val meta = document.documentElement.findElementByTagName("META") ?: fail()
            expect("Content-Type") { meta.getAttribute("http-equiv") }
            expect("text/html; charset=UTF-8") { meta.getAttribute("content") }
            expect("UTF-8") { charset }
        }
    }

    @Test fun `should parse html with no-value attribute`() {
        File("src/test/resources/html_with_no_value_attribute.html").inputStream().use {
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline()).apply {
                accept(it)
            }
            val document = htmlPipeline.result
            val select = document.documentElement.findElementByTagName("select") ?: fail()
            val options = select.childrenByType<Element>()
            expect(2) { options.size }
            expect("selected") { options[0].getAttribute("selected") }
        }
    }

    @Test fun `should parse html with missing end tags`() {
        File("src/test/resources/html_with_missing_end_tags.html").inputStream().use {
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline()).apply {
                accept(it)
            }
            val document = htmlPipeline.result
            val select = document.documentElement.findElementByTagName("select") ?: fail()
            val options = select.childrenByType<Element>()
            expect(2) { options.size }
            expect("selected") { options[0].getAttribute("selected") }
            expect("A") { HTML.trim(options[0].textContent) }
            expect("B") { HTML.trim(options[1].textContent) }
            val p = document.documentElement.findElementByTagName("p") ?: fail()
            expect(1) { p.children().size }
            expect("para") { HTML.trim(p.textContent) }
            val table = document.documentElement.findElementByTagName("table") ?: fail()
            val tableChildren = table.childrenByType<Element>()
            expect(2) { tableChildren.size }
            expect("thead") { tableChildren[0].tagName }
            expect("tbody") { tableChildren[1].tagName }
            val trs = tableChildren[1].childrenByType<Element>()
            expect(2) { trs.size }
            expect("tr") { trs[0].tagName }
            expect("tr") { trs[1].tagName }
            val tds = trs[1].childrenByType<Element>()
            expect(2) { tds.size }
            expect("td") { tds[0].tagName }
            expect("R2D1") { HTML.trim(tds[0].textContent) }
            expect("td") { tds[1].tagName }
            expect("R2D2") { HTML.trim(tds[1].textContent) }
        }
    }

    @Test fun `should parse html with CDATA`() {
        File("src/test/resources/html_with_cdata.html").inputStream().use {
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline()).apply {
                accept(it)
            }
            val document = htmlPipeline.result
            val body = document.documentElement.findElementByTagName("body") ?: fail()
            val cdata = body.childrenByType<CDATASection>()
            expect(1) { cdata.size }
            expect("abc") { cdata[0].textContent }
        }
    }

    @Test fun `should parse html with complex CDATA`() {
        File("src/test/resources/html_with_complex_cdata.html").inputStream().use {
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline()).apply {
                accept(it)
            }
            val document = htmlPipeline.result
            val body = document.documentElement.findElementByTagName("body") ?: fail()
            val cdata = body.childrenByType<CDATASection>()
            expect(2) { cdata.size }
            expect("[[X]]") { cdata[0].textContent }
            expect("<QQQ/>[[X]] ] ]] ") { cdata[1].textContent }
        }
    }

    @Test fun `should parse html with script`() {
        File("src/test/resources/html_with_script.html").inputStream().use {
            val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline()).apply {
                accept(it)
            }
            val document = htmlPipeline.result
            val script = document.documentElement.findElementByTagName("script") ?: fail()
            expect("Script <html>") { script.textContent }
            val style = document.documentElement.findElementByTagName("style") ?: fail()
            expect("Style <html>") { style.textContent }
        }
    }

    private fun Node.isComment(content: String) = this is Comment && textContent == content

    private fun Node.isAllWhiteSpace() = this is Text && HTML.isAllWhiteSpace(textContent)

    private fun Node.displayChildNodes() {
        val childNodes = childNodes
        for (i in 0 until childNodes.length)
            childNodes.item(i).displayNode()
    }

    private fun Node.displayNode() {
        when (this) {
            is Element -> {
                print("<$tagName")
                val attributes = attributes
                for (i in 0 until attributes.length)
                    attributes.item(i).let { print(" ${it.nodeName}=\"${it.nodeValue}\"") }
                print(">")
                if (!HTML.elementsWithoutChildren.contains(tagName.toLowerCase())) {
                    displayChildNodes()
                    print("</$tagName>")
                }
            }
            is Comment -> print("<!--$data-->")
            is Text -> print(data)
        }
    }

}

# html-pipeline

Simple parser for HTML, using the [`pipelines`](https://github.com/pwall567/pipelines) library.
This is not intended to be a strict parser of HTML5; the main planned use is to help with "screen-scraping" of HTML
websites.
It may also find use as a tool for testing HTML generation.

## Quick Start

Create a pipeline which feeds data into the `HTMLPipeline` object.
The result of the pipeline will be the `org.w3c.dom.Document` object.

```kotlin
    val htmlPipeline = DecoderFactory.getDecoder(Charsets.UTF_8, HTMLPipeline()).apply {
        accept(inputStream)
    }
    val document = htmlPipeline.result
```

## Dependency Specification

The latest version of the library is 0.1, and it may be obtained from the Maven Central repository.

### Maven
```xml
    <dependency>
      <groupId>net.pwall.html</groupId>
      <artifactId>html-pipeline</artifactId>
      <version>0.1</version>
    </dependency>
```
### Gradle
```groovy
    implementation 'net.pwall.html:html-pipeline:0.1'
```
### Gradle (kts)
```kotlin
    implementation("net.pwall.html:html-pipeline:0.1")
```

Peter Wall

2020-03-01

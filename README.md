# TXtruct

This is a Text-XML and XML-Text transformer.

## Build

Please set up JDK 11~ and Maven. Then
```
mvn package
```
will create the JAR file `./target/txtr-XX.jar`.

## Run

```
java -jar .../txtr-XX.jar <rule> [<input> [<output>]]
```
will transform `<input>` into `<output>` according to the `<rule>` file,
whose format is specified [here](txtr.html).

## Licence

TXtruct is licenced under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

c 2024 National Institute of Advanced Industrial Science and Technology（AIST）

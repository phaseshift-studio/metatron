# scratch

A small, disposable Maven project used as a fixture for the metatron agent IDE
(`ideInstSet` / codeSpace work). It exists to be parsed, edited, built, and
rebuilt without touching the metatron codebase.

## Layout

```
scratch/
├── pom.xml
├── src/
│   ├── main/java/com/example/scratch/
│   │   ├── Greeter.java
│   │   ├── Calculator.java
│   │   └── Operation.java
│   └── test/java/com/example/scratch/
│       └── GreeterTest.java
```

## Build

```bash
mvn -f src/test/resources/scratch/pom.xml test
```

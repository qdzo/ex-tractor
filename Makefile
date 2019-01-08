.PHONY : jar bin clean run run-bin package archive-project all native-bin make-test-files jvm lint test

APP=qdzo.ex-tractor-0.1.0-SNAPSHOT
FAT_JAR_FILE=$(APP)-standalone.jar
BIN_FILE=ex-tractor
JAR_FILE=$(BIN_FILE).jar

bin:
	time lein bin

clean:
	rm -rf target

jar:
	time lein uberjar
	cp target/uberjar/$(FAT_JAR_FILE) target/uberjar/$(JAR_FILE)

run:
	java -jar target/uberjar/$(FAT_JAR_FILE)

run-bin:
	./target/base+system+user+dev/$(APP)

native-bin:
	make target/native
	native-image -H:+ReportUnsupportedElementsAtRuntime  -jar target/uberjar/$(FAT_JAR_FILE)
	mv $(APP)-standalone target/native/

package:
	# cp target/base+system+user+dev/$(APP) $(BIN_FILE)
	mkdir -p target/package
	cp target/uberjar/$(FAT_JAR_FILE) target/package/$(JAR_FILE)
	java -jar target/package/$(JAR_FILE) -h > target/package/readme.md
	cd target/package && zip $(BIN_FILE).zip $(JAR_FILE) readme.md

default: jar

all: bin package

archive-project: clean
	rm ../$(BIN_FILE).zip
	cd .. && zip -r $(BIN_FILE).zip $(BIN_FILE) && cp $(BIN_FILE).zip ~/Dropbox/ && cd $(BIN_FILE)


jvm:
	jlink --output target/jvm --add-modules java.base

lint:
	lein nvd check
	lein eastwood

test:
	lein test

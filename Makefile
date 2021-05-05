GRADLE_VERSION=6.8.3
ILIVALIDATOR_VERSION=1.11.7
PROJECT_DIR=$(shell pwd)
TEST_DIR=${PROJECT_DIR}/test

.PHONY: clean
clean:
	rm -rf .gradle
	rm -rf build
	rm -rf gradle
	rm -rf .ilivalidator
	rm -f gradlew
	rm -f gradlew.bat


.gradle/.timestamp:
	mkdir .gradle
	touch $@

.gradle/gradle.zip: .gradle/.timestamp
	curl \
		-L \
		-o .gradle/gradle.zip \
		https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-all.zip

.gradle/.got: .gradle/gradle.zip
	unzip -d .gradle $^
	touch $@

.ilivalidator/.timestamp:
	mkdir .ilivalidator
	touch $@

.ilivalidator/ilivalidator.zip: .ilivalidator/.timestamp
	curl \
		-L \
		-o $@ \
		https://downloads.interlis.ch/ilivalidator/ilivalidator-${ILIVALIDATOR_VERSION}.zip

.ilivalidator/.got: .ilivalidator/ilivalidator.zip
	unzip -d .ilivalidator $^
	touch $@

gradle: .gradle/.got
	./.gradle/gradle-${GRADLE_VERSION}/bin/gradle -v

gradlew: gradle
	./.gradle/gradle-${GRADLE_VERSION}/bin/gradle wrapper

.PHONY: build
build: gradlew
	./gradlew jar

.PHONY: test
test: build .ilivalidator/.got
	java -jar .ilivalidator/ilivalidator-${ILIVALIDATOR_VERSION}.jar \
		--plugins build/libs \
		--models IlivalidatorAreaExtension \
		test/test_pg.xtf

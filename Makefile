.PHONY: build test integration format lint docs check

build:
	./gradlew build

test:
	./gradlew test

integration:
	docker compose up -d
	./gradlew integrationTest

docker-up:
	docker compose up -d

docker-down:
	docker compose down

format:
	./gradlew spotlessApply

lint:
	./gradlew detekt

docs:
	./gradlew dokkaHtml

check:
	./gradlew check

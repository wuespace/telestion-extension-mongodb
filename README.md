# telestion-extension-mongodb

A MongoDB connector for the Telestion backend.

## Install

First, add the extension package registry to your gradle repositories:

```groovy
repositories {
    maven {
        name = "GitHubMongoDB"
        url = uri("https://maven.pkg.github.com/wuespace/telestion-extension-mongodb/")
        credentials {
            username = System.getenv("GITHUB_ACTOR") == null ? GITHUB_ACTOR : System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN") == null ? GITHUB_TOKEN : System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Next, add the extension to the project dependency list:

```groovy
dependencies {
    // ...
    implementation 'de.wuespace.telestion.extension:mongodb:0.1.0'
    // ...
}
```

And synchronize gradle.

Finished!

## Usage

The extension provides different verticles, that you can use and set up in your project `config.json`.
You will need at least the
[MongoDatabaseService](https://wuespace.github.io/telestion-extension-mongodb/de/wuespace/telestion/extension/mongodb/MongoDatabaseService.html)
verticle to communicate with the Mongo-Database.

A really simple configuration could look like:
```json
{
  "org.telestion.configuration": {
    "app_name": "Basic Example",
    "verticles": [
      {
        "name": "Mongo Database Service",
        "verticle": "de.wuespace.telestion.extension.mongodb.MongoDatabaseService",
        "magnitude": 1,
        "config": {
          "host": "mongodb"
        }
      }
    ]
  }
}
```

with the `host` property specifying the hostname or ip address of the host machine running the mongo database.

Now, you can extend your configuration to store and request data from the database.

You can use the
[DataListener](https://wuespace.github.io/telestion-extension-mongodb/de/wuespace/telestion/extension/mongodb/DataListener.html)
verticle to listen to specific eventbus addresses and insert incoming messages into the database.

For example:

```json
{
  "org.telestion.configuration": {
    "app_name": "Basic Example",
    "verticles": [
      {
        "name": "Data Listener",
        "verticle": "de.wuespace.telestion.extension.mongodb.DataListener",
        "magnitude": 1,
        "config": {
          "listeningAddresses": [
            "example-data"
          ]
        }
      },
      {
        "name": "Mongo Database Service",
        "verticle": "de.wuespace.telestion.extension.mongodb.MongoDatabaseService",
        "magnitude": 1,
        "config": {
          "host": "mongodb"
        }
      }
    ]
  }
}
```

This configuration deploys a verticle which listens on the eventbus address `example-data`
and stores all incoming messages into the registered mongo database.
Every message type obtains its **own collection** in mongo.
To access the collections, use the full classname of the message type.

To extract data in periodic intervals, you can use the
[PeriodicDataAggregator](https://wuespace.github.io/telestion-extension-mongodb/de/wuespace/telestion/extension/mongodb/PeriodicDataAggregator.html)
verticle. It requests data in regular intervals,
combines them in a predefined format into one message
and sends it on the specified eventbus address.

For example:

```json
{
  "org.telestion.configuration": {
    "app_name": "Basic Example",
    "verticles": [
      {
        "name": "Mongo Database Service",
        "verticle": "de.wuespace.telestion.extension.mongodb.MongoDatabaseService",
        "magnitude": 1,
        "config": {
          "host": "mongodb"
        }
      },
      {
        "name": "Periodic Data Aggregator",
        "verticle": "de.wuespace.telestion.extension.mongodb.PeriodicDataAggregator",
        "magnitude": 1,
        "config": {
          "collection": "de.wuespace.telestion.project.daedalus2.messages.SystemT",
          "field": "imu.acc.x",
          "rate": 10,
          "outAddress": "aggregated-imu.acc.x"
        }
      }
    ]
  }
}
```

Here the `PeriodicDataAggregator` looks for a collection storing the `SystemT` message type
and tries to extract the `imu.acc.x` property which can be deeply nested.
After it gathered all required information, it publishes the data onto the `aggregated-imu.acc.x` eventbus address.
You can configure the aggregation rate via the `rate` property. The rate is represented as aggregations per second.

## Setup MongoDB

If you use our [Project Template](https://github.com/wuespace/telestion-project-template),
you can easily add MongoDB to your build and deploy pipeline.

Open the `docker-compose.yml` in the `application` folder and add the `mongodb` service:

```yaml
version: "3.9"

services:
  mongodb:
    # most recent and stable version off mongodb
    image: "mongo:4"
    profiles: ["devel-native", "devel-docker", "prod"]
    restart: always
    ports:
      # passthrough mongodb listen port to host for debugging
      - "127.0.0.1:27017:27017"
    volumes:
      # store database data for a later restart
      - type: volume
        source: mongodb-data
        target: "/data/db"
    # environment:
    #   # mongodb authentication
    #   MONGO_INITDB_ROOT_USERNAME: 'root'
    #   MONGO_INITDB_ROOT_PASSWORD: '12345'
    # bind to open ip to allow incoming connections from other network devices
    command: "--bind_ip 0.0.0.0"

  telestion-devel:
    image: "telestion-devel"
    profiles: ["devel-docker"]
    # ...
```

Do not forget to also update the `production.yml`:

```yaml
version: "3.7"

services:
  mongodb:
    # most recent and stable version off mongodb
    image: "mongo:4"
    restart: always
    ports:
      # passthrough mongodb listen port to host for debugging
      - "127.0.0.1:27017:27017"
    volumes:
      # store database data for a later restart
      - type: volume
        source: mongodb-data
        target: "/data/db"
    # environment:
    #   # mongodb authentication
    #   MONGO_INITDB_ROOT_USERNAME: 'root'
    #   MONGO_INITDB_ROOT_PASSWORD: '12345'
    # bind to open ip to allow incoming connections from other network devices
    command: "--bind_ip 0.0.0.0"

  telestion:
    image: "ghcr.io/wuespace/telestion-project-daedalus2:latest"
    restart: always
    # ...
```

### Contributing

For the documentation on contributing to this repository,
please take a look at the [Contributing Guidelines](./CONTRIBUTING.md).

## About

This is part of [Telestion](https://telestion.wuespace.de/), a project by [WüSpace e.V.](https://www.wuespace.de/).

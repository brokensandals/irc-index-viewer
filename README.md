# irc-index-viewer

A webapp for viewing IRC logs stored in an [elasticsearch](http://elasticsearch.org) index by [irc-indexer](https://github.com/brokensandals/irc-indexer).

## Usage

1. Setup [irc-indexer](https://github.com/brokensandals/irc-indexer).
2. Checkout the source code (you'll need leiningen to build it)
3. Get dependencies: `lein deps`
4. Build the war: `lein ring uberwar`
5. If desired/necessary - see Configuration below - set the system property `irc-index-viewer.configfile` in your servlet container to the location of the configuration file.
6. Deploy `irc-index-viewer-*-standalone.war`

### Configuration

If the system property `irc-index-viewer.configfile` is set it will be used as the location to read a config file from. Here's the format of the file, with the default values shown:

    {:index "http://localhost:9200/irc" ; the location of the elasticsearch index that irc-indexer is writing to
     :transcripts-per-page 15 ; when performing a search or showing most recent activity, how many documents to return
     :transcript-split-threshold 20} ; when showing most recent activity, continuous entries from one channel are grouped together until this many minutes of inactivity, after which they are split into two transcripts (the one with more recent entries being shown higher)

## License

Copyright (C) 2011 Jacob Williams

Distributed under the Eclipse Public License, the same as Clojure.

# Bag Database changelog

Forthcoming

- Update dependencies

2.7.3

- Add perl to Dockerfile to fix parsing GPS, metadata, and vehicle name topics
- Update dependencies

2.7.2

- Update dependencies

2.7.1

- Fix issue with indexing bags with GPS topics that don't have latitude/longitude fields
- Rewrite Dockerfile to produce smaller images
- Add docker-compose.yml for easy testing
- BagDB will wait up to 10s on startup for a database connection

2.7.0

- Fix manually setting metadata on bags without GPS coordinates
- Fix improper column types because created on newer Postgres DBs
- Fix issues decompressing LZ4-compressed chunks
- Updated dependencies

2.6.0

- Fix date rendering in grids
- Add batch inserts for GPS coordinates
- Support grayscale videos
- Lower video codec bitrate to improve performance
- Cap error list at 10000 entries

2.5.0

- Fix "Copy Link" not working in folder view
- Add ffmpeg to the Docker build
- Use ffmpeg to generate VP8 streams from Image topics
- Add optional flags to the /bags/search API call to fill in topics 
  and message types; not used by the GUI, but useful for other scripts
- Add support for editing, viewing, and searching by arbitrary tags
- Read metadata topics from bags into tags
- Remove MapQuest support
- Add support for loading tiles from generic tile map servers
- Fix a bug that would cause tabs to be blank after reloading
- Add an option to control whether entries should be removed from the DB if they are deleted from the filesystem
- Missing bags will be deleted by default
- Updated Spring & Hibernate-related dependencies to newer versions

2.4

- Fixing the mass vehicle name updater for the admin tool
- Saving widget states between reloads
- Adding more database indexes to improve search performance
- Implementing a hierarchical folder view for the bag list
- Adding some more button icons

2.3

- Updating the bag reader library to 1.5 to handle errors better
- Using Liquibase to handle database versioning and migration
- Using spatial columns for storing data for bags and their positions
- Dropping MySQL support
- Switching to H2 for the embedded database
- Removing Hibernate Search support; it wasn't actually being used and just made things slower
- Adding support for LZ4 compression
- Using Spring Session for user session tracking; sessions no longer time out

2.2

- Adding OpenCV to handle Bayer image formats
- Adding the ability to see the first image on a topic
- Adding Copy Link(s) buttons to the context menu in the grid that will copy links to download bag files to the clipboard
- Minifying Javascript for the release build
- Adding a "debugJavascript" config option that will cause the non-minified files to load
- Fixing some Javascript syntax errors

2.1

- Vehicle name topics are configurable
- GPS topics are configurable
- If no configured GPS topics are found, it will try to find one based on known message types
- Updating bag-reader-java to 1.2
- Adding Travis CI support
- Expanding on GPS-related documentation

2.0

- Adding Tomcat manually installation instructions
- Removed the "Name" database column; it was useless
- Bumping the version number to 2.0 for the open source release
- Consistently using the name "bag-database" between the Docker image and the Maven artifact name
- Adding the 3-clause BSD license
- Adding attributions for all of the dependencies
- Updated some dependencies
- Extracted bag reading a deserializing functionality into a separate library
- Changed the Maven groupId to com.github.swri-robotics
- Changed the package name to com.github.swrirobotics
- Preventing the user from dragging windows outside of the viewable area
- Fixing an issue that would make map tile credentials invisible

1.7

- Displaying visible warnings when searching by both message types and topic names
- Optimizing some CSS
- Adding the ability to download multiple bags at once
- Adding the ability to plot multiple bags on a single map
- Couldn't save bag files
- Couldn't search on the description field
- Using the Java 1.8 stream operators where possible for parallelizability

1.6

- Adding checkboxes for controlling the full text search fields
- Enabling filtering for all grid columns
- Switching the Bag grid to a BufferedStore for faster loading
- Disabling inline editing on the main grid due to an ExtJS bug
- Handling sorting and pagination on the remote side
- Adding /vehicle/gps/fix to the list of known GPS topics

1.5

- Handle relative URLs properly
- Enable gzip compression in Tomcat for Docker
- ExtJS updated to 6.0.1
- OpenLayers updated to 3.51.1
- Added an "About" window
- A navigation button has been added with links to the admin and config pages
- Administrative privileges are required to access the admin and config pages
- The admin password can be set through the config file on startup
- CSRF protection has been implemented
- Handling changing the bag path at runtime
- Added a Dockerfile and a maven plugin for building a Docker image
- Rewrote bag hashing to be more reliable
- Cleaned up and documented lots of code
- Improved bag reading performance
- Removed the "DuplicateBag" table and related functionality; it was just a workaround due to hashing bags being very slow

1.4

- Implemented generic message deserialization
- Removed hand-written deserialization classes

1.3

- Sped up bag hashing

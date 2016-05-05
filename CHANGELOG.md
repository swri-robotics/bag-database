# Bag Database changelog

2.1-SNAPSHOT

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

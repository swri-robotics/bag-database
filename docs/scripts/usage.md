---
layout: default
title: Usage
parent: Scripts
nav_order: 1
description: "Script Usage"
permalink: /scripts/usage
---

# Usage

So, you've got an idea for an script.  How do they actually work?

## Running

When a script runs on a bag file, the Bag Database takes the following steps:

1. Write the script to a temporary file
2. Pull the requested Docker image
3. Create a new Docker container based on that image
4. Mount the temporary script file and all the selected bags as volumes in that container
5. Execute the script and pass in all the bag files as command line arguments

After the script finishes, it saves its stdout and stderr output into the database.
That's it!  Simple, right?

## Script Output

Your script can output anything you want, but there are some special features available if
you print a JSON object with some specific keys.  For example:

```json
{
  "addTags" : {
     "message_count" : 602
  },
  "removeTags" : [ "old_tag" ],
  "setDescription" : "Processed",
  "setGpsCoordinates" : {
     "latitude" : 30,
     "longitude" : 60
  },
  "setLocation" : "Here",
  "setVehicle" : "Something"
}
```

Each of these tags has a specific effect.  Note that your output is not limited to these keys;
any other values in your JSON output will be ignored.

### `addTags`

Any key:value pairs in this object will be added as tags to all of the processed bag files.

### `removeTags`

Any tags with keys in this list will be removed from all processed bag files.

### `setDescription`

If this key is present, all processed bag files will have their description set to this value.

### `setGpsCoordinates`

If this key contains a valid latitude & longitude, all process bag files will have their
latitude and longitude set to this value.

### `setLocation`

If this key is present, all processed bag files will have their location set to this value.

### `setVehicle`

If this key is present, all processed bag files will have their vehicle name set to this value.

## Advanced Usage

The example presented here is just a short, single file, but you might need to do something
much more complex.  Here's some ideas for how to go about that:

### More Complex Scripts and Dependencies

If you need extra libraries or modules, consider making a Docker image that contains everything
you need and just having the script act as the entry point for your container.  Alternatively,
bundle everything you need into a package, put it on a web server, and then have your script
download and execute it.

### Programmatic Execution

Let's say you want to do something like have a vehicle that automatically uploads bag files
and runs a specific script on them.  You could use the [Bag Database API](../rest-api) to do so,
and then after the script has finished, retrieve its output.

### Marking Interesting Bag Files

Since it's possible to search for tags based on their text, if you have a script that can decide
a bag is "interesting" -- for example, because it has GPS coordinates in a certain location, or
because your script recognized something in one of its images, or because an error was logged
in its output -- you could have an automatic script mark those bags with a tag to make it easier
to find them.  To make it even fancier, if you allow your script to have network access, you could
automatically e-mail specific people whenever an interesting bag is uploaded.

### Setting Criteria to Restrict Scripts

It's convenient to automatically run scripts every time you add a new bag file, but if
your script takes a long time to run or if you don't need them to run for most bag files,
you can set [Script Criteria](../scripts/#run-criteria) to control whether they're automatically run.
Consider placing your bag files for a specific project in a project-specific directory
or naming them with a particular pattern that makes it easy to filter them.
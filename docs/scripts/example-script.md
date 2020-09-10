---
layout: default
title: Example Script
parent: Scripts
nav_order: 2
description: "Example Script"
permalink: /scripts/example-script
---

# Example Script

Need something you can copy & paste as a starting point?  Here's a simple Python script that
will find the average velocities of an IMU and save it to a tag.

```python
#!/usr/bin/env python
# Example bag file statistics script.
# It takes a list of arguments, the names of bag files to process, and prints
# a JSON string that contains a map of key:value pairs indicating statistics
# and their values.
#
# Example:
#   $ ./main.py bagfile1.bag bagfile2.bag bagfile3.bag
#   {
#     "average_x_velocity": 0.0036972600463926197,
#     "addTags": {
#       "average_velocity": 5.0540553416313175e-8
#     },
#     "average_z_velocity": -0.04822304991761035,
#     "average_y_velocity": -0.0010902862065158795
#   }

import argparse
import json
import math
import rosbag
import sys


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Generate statistics from a bag file.')
    parser.add_argument('bagfiles', metavar='FILENAMES', type=str, nargs='+', help='The path to the bag files.')
    args = parser.parse_args()

    output = {
        'addTags': {}
    }
    angular_x_velocities = []
    angular_y_velocities = []
    angular_z_velocities = []

    for bagfile in args.bagfiles:
        bag = rosbag.Bag(bagfile)
        for topic, msg, t in bag.read_messages(topics=['/localization/imu/raw']):
            angular_x_velocities.append(msg.angular_velocity.x)
            angular_y_velocities.append(msg.angular_velocity.y)
            angular_z_velocities.append(msg.angular_velocity.z)

    output['average_x_velocity'] = sum(angular_x_velocities) / len(angular_x_velocities)
    output['average_y_velocity'] = sum(angular_y_velocities) / len(angular_y_velocities)
    output['average_z_velocity'] = sum(angular_z_velocities) / len(angular_z_velocities)

    overall_avg = math.sqrt((output['average_x_velocity'] ** 2) *
                            (output['average_x_velocity'] ** 2) *
                            (output['average_x_velocity'] ** 2))

    output['addTags']['average_velocity'] = overall_avg

    json.dump(output, sys.stdout)
```
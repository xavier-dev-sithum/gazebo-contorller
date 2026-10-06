#!/usr/bin/env bash
# Serve the Gazebo camera as RTSP: rtsp://<this PC>:8564/drone
# Start the sim with the camera model first: ./start_sim.sh  (uses gz_x500_mono_cam)
cd "$(dirname "$0")"
exec "${MEDIAMTX:-$HOME/tools/mediamtx/mediamtx}" mediamtx-drone.yml

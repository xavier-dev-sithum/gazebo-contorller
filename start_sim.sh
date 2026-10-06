#!/usr/bin/env bash
# Start PX4 SITL + Gazebo Harmonic: x500 with a CGO3 gimbal camera in the Baylands park world.
#   ./start_sim.sh                      -> GUI
#   HEADLESS=1 ./start_sim.sh           -> no Gazebo window
#   PX4_GZ_WORLD=default ./start_sim.sh -> empty world (fast)
# The app connects to PX4's GCS MAVLink port: udpout://<this PC>:18570
cd "$(dirname "$0")/PX4-Autopilot" && PX4_GZ_WORLD="${PX4_GZ_WORLD:-baylands}" make px4_sitl gz_x500_gimbal

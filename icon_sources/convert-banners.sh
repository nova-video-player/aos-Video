#!/bin/bash
# src must be 1024x1024 size
srcn=nova_banner

# Define arrays for resolutions and directory names
resolutions=(160 240 320 480 640)
densities=("mdpi" "hdpi" "xhdpi" "xxhdpi" "xxxhdpi")

# Create densities if they don't exist
for dir in "${densities[@]}"
do
  mkdir -p "res/mipmap-${dir}"
done

# Associate resolutions with directory names using associative array
declare -a dir_map
for ((i=0; i<${#resolutions[@]}; i++))
do
  dir_map[${resolutions[$i]}]=${densities[$i]}
done

# Loop through resolutions, resize images, and save them in the appropriate directory
for resolution in "${resolutions[@]}"
do
  dir="${dir_map[$resolution]}"
  yresolution=$(echo "scale=0; 9 * $resolution / 16" | bc)
  convert ${srcn}.png -resize ${resolution}x${yresolution} -quality 100 res/mipmap-${dir}/${srcn}.webp
done

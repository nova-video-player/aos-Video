#!/bin/bash
# src must be 1024x1024 size
srcn=nova
srca=nova_foreground
srcr=nova_round
srcp=nova_private

# Define arrays for resolutions and directory names
#resolutions=(256 384 512 768 1024)
resolutions=(80 120 160 240 320)
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
  echo convert ${srcn}.png -resize ${resolution}x${resolution} -quality 100 res/mipmap-${dir}/${srcn}.webp
  convert ${srcn}.png -resize ${resolution}x${resolution} -quality 100 res/mipmap-${dir}/${srcn}.webp
  convert ${srca}.png -resize ${resolution}x${resolution} -quality 100 res/mipmap-${dir}/${srca}.webp
  convert ${srcr}.png -resize ${resolution}x${resolution} -quality 100 res/mipmap-${dir}/${srcr}.webp
  convert ${srcp}.png -resize ${resolution}x${resolution} -quality 100 res/mipmap-${dir}/${srcp}.webp
done

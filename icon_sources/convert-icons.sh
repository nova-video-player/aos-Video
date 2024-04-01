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

# foreground needs to be scaled otherwise icon is too big
convert nova_foreground.png -resize 67% -gravity center -background none -extent 1024x1024 nova_foreground2.png

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
  echo convert size ${resolution}x${resolution} for res/mipmap-${dir}
  convert ${srcn}.png -resize ${resolution}x${resolution} -quality 100 res/mipmap-${dir}/${srcn}.webp
  convert ${srca}2.png -resize ${resolution}x${resolution} -quality 100 res/mipmap-${dir}/${srca}.webp
  convert ${srcr}.png -resize ${resolution}x${resolution} -quality 100 res/mipmap-${dir}/${srcr}.webp
  convert ${srcp}.png -resize ${resolution}x${resolution} -quality 100 res/mipmap-${dir}/${srcp}.webp
done

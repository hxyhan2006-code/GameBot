# Public gameplay stills used for pixel regression tests

These are small JPEG reference stills, not game assets for the app. They are included
only in JVM test resources and are not shipped in the APK. Copyright remains with
the respective gameplay authors/game publisher. We use them to test scene rejection
and pixel observations, not to imply endorsement, automation permission or proof of
physical input. Original video time offsets:

- training.jpg: 4s; menu.jpg: 2s, https://www.reddit.com/r/pesmobile/comments/1x5sfjm/double_touch_not_working/
  video: https://v.redd.it/jkh0we98yfvh1/CMAF_1080.mp4?source=fallback
- training-advert.jpg / training-radar-noise.jpg: later frames from the same training video,
  approximately 7s / 10.5s (sampled at 2 fps over 3–11s). The first contains cyan advertising
  alongside the actual player triangle; the second has separated gold radar fragments/noise.
  Used for native-radar and marker regression, not as evidence of the owner's phone input.
- replay.jpg: 2s, https://www.reddit.com/r/pesmobile/comments/1xjacfp/may_i_know_why_the_defender_stopped_and_moving/
  video: https://v.redd.it/f5mpszyflkyh1/CMAF_1080.mp4?source=fallback
- match.jpg: 3s, https://www.reddit.com/r/pesmobile/comments/1usj8vo/offline_input_delay_proof_pressure_button_doesnt/
  video: https://v.redd.it/c32kixhfldch1/CMAF_1080.mp4?source=fallback

Extracted with ffmpeg -ss OFFSET -frames:v 1 -q:v 5. These four examples do not
establish compatibility with every pitch, camera, layout, version or device.

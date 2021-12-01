# MapLoad

Load each chunk of the world.  This plugin crawls through every chunk
within the WorldBorders of a given world and will load it once.  It
can work across several restarts.  It was created because there was a
need to update all chunks to the 1.15 format or else Dynmap would
render black holes.  The reasons for this are not understood.

In the future, this could be used in modified form to pre-generate
worlds or modify all chunks in some other way.

## Commands

- `/mapload [world]` - Start the crawling process, either in the
  player's world, or in the named world.

## Files

- `plugins/MapLoad/state.json`: The serialized state of chunk loading.
  Will be deleted once the process is complete.

## Shortcomings

Only one world can be done at a time.
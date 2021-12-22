<template>
  <div>
    <div class="entry">
      <div>
        <a :href="`https://open.spotify.com/album/${track.album.id}`">
          <img
            :src="track.album.url"
            width="80"
            :style="`border: 5px solid ${moodColor(track.mood)}`"
          />
        </a>
        <img
          v-on:click="remove_callback(track.id)"
          class="ignore"
          src="http://cdn.onlinewebfonts.com/svg/img_384828.png"
          width="20"
        />
      </div>
      <p>
        <a :href="`https://open.spotify.com/track/${track.id}`">{{
          `${track.name}`
        }}</a>
        <br />
      </p>
    </div>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import { BasicTrack } from "@/models";
import { moodColor } from "@/models";

@Options({
  props: {
    track: Object,
    remove_callback: Function,
  },
  components: {},
  methods: {
    moodColor: moodColor,
  },
})
export default class TrackView extends Vue {
  track!: BasicTrack;
  remove_callback!: any;
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
.ignore {
  position: absolute;
  top: 0;
  right: 5px;
}

.ignore:hover {
  cursor: pointer;
}

.entry {
  width: 100px;
  height: 100%;
}

.entry img {
  margin: 5px;
}

h3 {
  margin: 40px 0 0;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin: 0 10px;
}
a {
  color: #42b983;
}
</style>

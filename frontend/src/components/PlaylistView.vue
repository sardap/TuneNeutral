<template>
  <div>
    <row container :gutter="10" :columns="5">
      <column v-for="track in tracks" :key="track.id">
        <div class="entry">
          <a :href="`https://open.spotify.com/album/${track.album.id}`">
            <img
              :src="track.album.url"
              width="80"
              :style="`border: 5px solid ${moodColor(track.mood)}`"
            />
          </a>
          <p>
            <a :href="`https://open.spotify.com/track/${track.id}`">{{
              `${track.name}`
            }}</a>
            <br />
          </p>
        </div>
      </column>
    </row>
    <AddToQueue :date="date" />
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import { BasicTrack } from "@/models";
import AddToQueue from "@/components/AddToQueue.vue";
import { moodColor } from "@/models";

@Options({
  props: {
    tracks: Array,
    date: String,
  },
  components: {
    AddToQueue,
  },
  methods: {
    moodColor: moodColor,
  },
})
export default class PlaylistView extends Vue {
  tracks!: BasicTrack[];
  date!: string;
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
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

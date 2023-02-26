<template>
  <div>
    <row container :gutter="10" :columns="5">
      <column v-for="track in tracks" :key="track.id">
        <TrackView :remove_callback="remove_callback" :track="track" />
      </column>
    </row>
    <div>
      <button class="button" @click="copyShareText">Copy Playlist to Clipboard!</button>
    </div>
    <div>
      <div v-if="note">
        <h3>Note</h3>
        <p>{{ note }}</p>
      </div>
      <div v-else>
        <p>No note for this day</p>
      </div>
    </div>
    <div v-if="update_spotify">
      <AddToQueue :date="date" v-on:click="update_spotify = false" />
    </div>
    <div v-else>
      <a
        :href="`https://open.spotify.com/playlist/${spotify_playlist}`"
        target="_blank"
        class="button spotify-link"
      >
        Open Playlist
      </a>
    </div>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import { BasicTrack } from "@/models";
import TrackView from "@/components/TrackView.vue";
import AddToQueue from "@/components/AddToQueue.vue";

@Options({
  props: {
    tracks: Array,
    date: String,
    note: String,
    remove_callback: Function,
  },
  components: {
    TrackView,
    AddToQueue,
  },
  methods: {
    async getPlaylistId() {
      let response = await fetch(`/v1/api/spotify_playlist`);
      if (response.status == 200) {
        let apiRes = await response.json();
        if (apiRes.result && apiRes.result.id) {
          this.spotify_playlist = apiRes.result.id;
        }
      }
    },
    async copyShareText() {
      let result = "";
      result += `😭 Tune Neutral for ${this.date} 😃\n`;
      for (let i = 0; i < this.tracks.length; i++) {
        let track = this.tracks[i];
        result += `${i+1}. ${track.name} https://open.spotify.com/track/${track.id}\n`;
      }
      result += "From https://tune.sarda.dev\n";
      navigator.clipboard.writeText(result);
    }
  },
  created() {
    this.getPlaylistId();
  },
  data() {
    return {
      spotify_playlist: "",
      update_spotify: true,
    };
  },
})
export default class PlaylistView extends Vue {
  tracks!: BasicTrack[];
  date!: string;
  note!: string;
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
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
</style>

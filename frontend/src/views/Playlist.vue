<template>
  <div class="playlist">
    <h2>Mood For: {{ date }}</h2>
    <div v-if="tracks.length > 0">
      <PlaylistView
        :tracks="tracks"
        :date="date"
        :note="note"
        :remove_callback="removeTrack"
      />
    </div>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import PlaylistView from "@/components/PlaylistView.vue"; // @ is an alias to /src

@Options({
  components: {
    PlaylistView,
  },
  methods: {
    async removeTrack(trackId: string) {
      await fetch(`/v1/api/remove_track/${trackId}`, {
        method: "POST",
      });
      alert("That track will not be used anymore.");
    },
    async getPlaylist() {
      let response = await fetch(`/v1/api/mood_playlist/${this.date}`);
      let apiRes = await response.json();
      this.tracks = apiRes.result.tracks;
      this.note = apiRes.result.note;
    },
  },
  created() {
    this.getPlaylist();
  },
  data() {
    return {
      date: this.$route.query.date,
      tracks: [],
      note: null,
    };
  },
})
export default class About extends Vue {}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
.playlist {
  margin-left: 20%;
  margin-right: 20%;
}
</style>

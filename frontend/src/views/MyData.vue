<template>
  <div>
    <h2>All My Data</h2>
    <div v-if="ignored_tracks.length > 0">
      <h3>Ignored Tracks</h3>
      <PlaylistView :tracks="ignored_tracks" :remove_callback="unRemove" />
    </div>
    <div>
      <h3>Data</h3>
      <div class="data-dump">
        <pre>{{ complete_data }}</pre>
      </div>
      <div class="button delete" v-on:click="clearData()">
        Delete all my data
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import router from "@/router";
import PlaylistView from "@/components/PlaylistView.vue";

@Options({
  components: {
    PlaylistView,
  },
  methods: {
    async unRemove(trackId: string) {
      await fetch(`/v1/api/unremove_track/${trackId}`, {
        method: "POST",
      });
      alert("Track has been added back into the shuffle");
      this.getRemovedTracks();
    },
    async getRemovedTracks() {
      let response = await fetch(`/v1/api/removed_tracks`);
      if (response.status == 200) {
        let apiRes = await response.json();
        if (apiRes.result && apiRes.result.tracks) {
          this.ignored_tracks = apiRes.result.tracks;
        }
      }
    },
    async getAllData() {
      let response = await fetch(`/v1/api/all_data`);
      let apiRes = await response.json();
      this.complete_data = JSON.stringify(apiRes, null, 2);
    },
    async clearData() {
      await fetch(`/v1/api/remove_all_user_data`, {
        method: "DELETE",
      });
      router.push(`/`);
    },
  },
  created() {
    this.getAllData();
    this.getRemovedTracks();
  },
  data() {
    return {
      complete_data: "Loading...",
      ignored_tracks: [],
    };
  },
})
export default class MyData extends Vue {}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
.delete {
  background: red;
}

.data-dump {
  text-align: left;
  max-width: 80%;
  margin-left: 10%;
  word-wrap: break-word;
}
</style>

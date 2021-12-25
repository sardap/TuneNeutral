<template>
  <div class="create-playlist">
    <h2>Report A Feeling</h2>
    <CreatePlaylist @playlist_created="playlistCreated" />
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import CreatePlaylist from "@/components/CreatePlaylist.vue";
import router from "@/router";

@Options({
  components: {
    CreatePlaylist,
  },
  methods: {
    async playlistCreated(value: string) {
      router.push(`playlist?date=${value}`);
    },
  },
  data() {
    return {};
  },
})
export default class Home extends Vue {}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped></style>

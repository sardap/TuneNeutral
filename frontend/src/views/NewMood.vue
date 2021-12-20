<template>
  <div>
    <CreatePlaylist @playlist_created="playlistCreated" />
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import router from "@/router";
import CreatePlaylist from "@/components/CreatePlaylist.vue";

@Options({
  components: {
    CreatePlaylist,
  },
  methods: {
    async playlistCreated(value: string) {
      router.push(`playlist?date=${value}`);
    },
  },
})
export default class Home extends Vue {}
</script>

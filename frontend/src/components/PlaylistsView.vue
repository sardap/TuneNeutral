<template>
  <div>
    <div class="entry" v-for="playlist in playlists" :key="playlist.date">
      <p>
        <a :href="`/#/playlist?date=${playlist.date}`">{{
          `${playlist.date}`
        }}</a>
      </p>
      <hr />
    </div>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";

@Options({
  props: {
    playlists: Array,
  },
})
export default class PlaylistsView extends Vue {
  playlists!: any[];
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
a {
  color: #42b983;
}
</style>

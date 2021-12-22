<template>
  <div>
    <div v-if="loading">loading...</div>
    <div v-else>
      <div class="button" v-on:click="addToQueue()">Update Playlist!</div>
    </div>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";

@Options({
  props: {
    date: String,
  },
  methods: {
    async addToQueue() {
      this.loading = true;
      let response = await fetch(`/v1/api/update_playlist/${this.date}`, {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({}),
      });
      await response.json();
      this.loading = false;
    },
  },
  data() {
    return {
      loading: false,
    };
  },
})
export default class AddToQueue extends Vue {
  date!: string;
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

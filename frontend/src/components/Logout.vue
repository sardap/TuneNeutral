<template>
  <div>
    <a class="button logout" href="/logout">Logout</a>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";

@Options({})
export default class Logout extends Vue {}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
@import "../scss/core.scss";

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
</style>

<template>
  <div id="root">
    <div>
      <h3>What is this?</h3>
      <p>
        Tune Neutral will create an mood playlist to get you back into the
        middle. Feeling too happy will give you sad music.
      </p>
    </div>
    <div>
      <h3>How?</h3>
      <p>
        The Spotify Web API has a field for how happy or sad a song is. This is
        used to calculate how many tracks you should need to get back to the
        middle. It uses your spotify login to track you so no need to create an
        account.
      </p>
    </div>
    <h3><b>This site is NOT associated or endorsed by Spotify</b></h3><br/>
    <div>
      <h3>Links</h3>
      <ul>
        <li><a href="https://github.com/sardap/TuneNeutral">Source Code</a></li><br/>
        <li><a href="https://sarda.dev">Paul Sarda Portfolio site</a></li><br/>
        <li><a href="paul@sarda.dev">paul@sarda.dev</a></li><br/>
      </ul>
    </div>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";

@Options({
  props: {},
  components: {},
  methods: {},
})
export default class About extends Vue {}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
h3 {
  margin: 0px;
  padding: 0px;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin-top: 10px;
}
a {
  color: #42b983;
}
#root div {
  width: 500px;
  margin: auto;
  max-width: 60%;
}
#root {
  margin-top: 30px;
  margin-left: 10%;
  margin-right: 10%;
}
</style>

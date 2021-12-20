<template>
  <div>
    <div>Create an Account</div>
    <form action="/create">
      <label for="username">Username:</label>
      <input type="text" id="username" name="username" />
      <label for="password">Password:</label>
      <input type="password" id="password" name="password" />
      <input type="submit" value="Submit" />
    </form>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import { moodColor, roundMood } from "@/models";
import Loading from "vue-loading-overlay";
import "vue-loading-overlay/dist/vue-loading.css";

@Options({
  components: {
    Loading,
  },
  props: {},
  methods: {
    async createPlaylist() {
      this.is_loading = true;
      let response = await fetch(`/v1/api/generate_mood_playlist`, {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ mood: this.mood, date: this.date }),
      });
      await response.json();
      this.is_loading = false;
      this.$emit("playlist_created", this.date);
    },
    setMood(mood: number) {
      this.mood = mood;
      this.loading_colour = moodColor(roundMood(mood));
    },
    moodToString(mood: number): string {
      switch (mood) {
        case -0.5:
          return "Very Sad";
        case -0.125:
          return "Sad";
        case 0.0:
          return "Nothing";
        case 0.125:
          return "Good";
        case 0.5:
          return "Very Good";
      }

      return "unknown";
    },
  },
  data() {
    let date = new Date();
    return {
      mood: 0.0,
      is_loading: false,
      loading_colour: moodColor(0.0),
      date: `${date.getFullYear()}-${(date.getMonth() + 1)
        .toString()
        .padStart(2, "0")}-${date.getDate().toString().padStart(2, "0")}`,
    };
  },
})
export default class Login extends Vue {}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
.mood {
  display: inline-block;
  color: black;
  border: 1px solid #ccc;
  background: rgb(211, 144, 144);
  box-shadow: 0 0 5px -1px rgba(0, 0, 0, 0.2);
  width: 100%;
  height: 50px;
  line-height: 50px;
  text-align: center;
  cursor: pointer;
  border-radius: 25px;
}

span {
  display: inline-block;
  vertical-align: middle;
  line-height: normal;
}

.mood:active {
  color: whitesmoke;
  box-shadow: 0 0 5px -1px rgba(0, 0, 0, 0.6);
}

.button {
  display: inline-block;
  color: #444;
  border: 1px solid #ccc;
  background: #ddd;
  box-shadow: 0 0 5px -1px rgba(0, 0, 0, 0.2);
  cursor: pointer;
  vertical-align: middle;
  max-width: 100px;
  padding: 5px;
  text-align: center;
  margin-top: 20px;
}

.button:active {
  color: red;
  box-shadow: 0 0 5px -1px rgba(0, 0, 0, 0.6);
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

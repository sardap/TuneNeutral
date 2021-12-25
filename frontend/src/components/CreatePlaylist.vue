<template>
  <div>
    <div class="vld-parent">
      <loading
        v-model:active="is_loading"
        :can-cancel="false"
        :is-full-page="true"
        :drag-on-click="true"
        :color="loading_colour"
        blur="3px"
        :tooltip="'none'"
      />
      <div>
        <h3>How you feeling {{ compliment }}?</h3>
        <vue-slider
          class="slider"
          v-model="mood"
          :min="-0.5"
          :max="0.5"
          :interval="0.01"
          :marks="marks"
          :process="process"
        />
      </div>
      <p>Current Mood: {{ moodToString(this.mood) }}</p>
      <label for="note">Note:</label><br />
      <textarea id="note" v-model="note" name="note" placeholder="" /><br />
      <label for="date">Date:</label><br />
      <input v-model="date" name="date" placeholder="" /><br />
      <div class="button" v-on:click="createPlaylist()">Report Mood</div>
    </div>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import { moodColor, MUST_AUTH, roundMood } from "@/models";
import Loading from "vue-loading-overlay";
import "vue-loading-overlay/dist/vue-loading.css";
import VueSlider from "vue-slider-component";
import "vue-slider-component/theme/default.css";

@Options({
  components: {
    Loading,
    VueSlider,
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
        body: JSON.stringify({
          mood: this.mood,
          date: this.date,
          note: this.note,
        }),
      });
      const apiRes = await response.json();
      this.is_loading = false;
      if (response.status == 200) {
        this.$emit("playlist_created", this.date);
      } else {
        this.$emit(MUST_AUTH, apiRes.result);
      }
    },
    setMood(mood: number) {
      this.mood = mood;
      this.loading_colour = moodColor(roundMood(mood));
    },
    moodToString(mood: number): string {
      switch (roundMood(mood)) {
        case -0.25:
          return "Very Sad";
        case -0.125:
          return "Sad";
        case 0.0:
          return "Nothing";
        case 0.125:
          return "Good";
        case 0.25:
          return "Very Good";
      }

      return "unknown";
    },
  },
  data() {
    let date = new Date();
    let compliments = [
      "Big Guy",
      "Shit Bag",
      "Handsome",
      "Ugly",
      "You Human Piece of Shit",
      "Beautiful",
      "Gorgeous",
      "Good-looking",
      "Fine Thing",
      "Symmetrical Face",
      "Shit for Brains",
      "Cunt",
      "Mate",
      "Dick Head",
      "Fuck Face",
      "Garbage Brains",
      "Dude",
    ];

    return {
      mood: 0.0,
      is_loading: false,
      compliment: compliments[(Math.random() * compliments.length) | 0],
      loading_colour: moodColor(0.0),
      note: "",
      date: `${date.getFullYear()}-${(date.getMonth() + 1)
        .toString()
        .padStart(2, "0")}-${date.getDate().toString().padStart(2, "0")}`,
      marks: {
        "-0.5": "😭",
        "-0.25": "😢",
        "-0.125": "😞",
        "0": "😑",
        "0.125": "😊",
        "0.25": "😆",
        "0.5": "🤗",
      },
      process: (dotsPos: number[]) => {
        return [
          [
            50,
            dotsPos[0],
            { backgroundColor: moodColor(roundMood(this.mood)) },
          ],
        ];
      },
    };
  },
})
export default class PlaylistView extends Vue {}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
.slider {
  margin-left: 10%;
  margin-right: 10%;
}

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

div {
  margin-block-end: 50px;
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

#note {
  width: 80%;
  height: 100px;
}

.button {
  margin-top: 40px;
  font-size: 20px;
}
</style>

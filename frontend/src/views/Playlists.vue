<template>
  <div class="home">
    <h2>Playlists</h2>
    <div v-if="playlists.length > 0">
      <Calendar
        :attributes="attributes"
        v-model="date"
        @dayclick="dayClicked"
      />
      <!-- <PlaylistsView :playlists="playlists" /> -->
    </div>
    <div v-else>
      <p>No playlists yet</p>
    </div>
  </div>
</template>

<script lang="ts">
import { Options, Vue } from "vue-class-component";
import PlaylistsView from "@/components/PlaylistsView.vue";
import { Calendar } from "v-calendar";
import { roundMood } from "@/models";
import router from "@/router";

@Options({
  components: {
    PlaylistsView,
    Calendar,
  },
  methods: {
    moodColor(mood: number): string {
      switch (mood) {
        case -0.25:
          return "red";
        case -0.125:
          return "orange";
        case 0.0:
          return "gray";
        case 0.125:
          return "teal";
        case 0.25:
          return "green";
      }

      return "black";
    },
    async getPlaylists() {
      let response = await fetch(`/v1/api/mood_playlists`);
      let apiRes = await response.json();
      if (!apiRes.result.playlists) {
        return;
      }
      this.playlists = apiRes.result.playlists;
      for (let playlist of this.playlists) {
        let highlight = this.moodColor(roundMood(playlist.start_mood));
        this.attributes.push({
          key: "playlist",
          // Attribute type definitions
          highlight: {
            color: highlight,
            fillMode: "outline",
          },
          // We also need some dates to know where to display the attribute
          // We use a single date here, but it could also be an array of dates,
          //  a date range or a complex date pattern.
          dates: Date.parse(playlist.date),
        });
      }
    },
    dayClicked(day: any) {
      if (!day || !day.attributes || day.attributes.length == 0) {
        return;
      }

      router.push(`playlist?date=${day.id}`);
    },
  },
  created() {
    this.getPlaylists();
  },
  data() {
    return {
      playlists: [],
      attributes: [],
    };
  },
})
export default class About extends Vue {}
</script>

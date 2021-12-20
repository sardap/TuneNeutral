export interface BasicTrack {
    name: string;
    id: string;
    album: {
        name: string;
        id: string;
    };
    artists: {
        name: string;
        id: string;
    }[];
}

export const MUST_AUTH = "must_auth";


export function roundMood(mood: number): number {
    if (mood >= -0.5 && mood < -0.1875) {
        return -0.25;
    } else if (mood >= -0.1875 && mood < -0.0625) {
        return -0.125;
    } else if (mood >= -0.0625 && mood < 0.0625) {
        return 0.0;
    } else if (mood >= 0.0625 && mood < 0.1875) {
        return 0.125;
    } else if (mood >= 0.1875 && mood <= 0.5) {
        return 0.25;
    }

    return 0.0;
};


export function moodColor(mood: number): string {
    switch (mood) {
        case -0.25:
            return "#FF0000";
        case -0.125:
            return "#E96A6A";
        case 0.0:
            return "#d3d3d3";
        case 0.125:
            return "#6AE96A";
        case 0.25:
            return "#00FF00";
    }

    return "black";
};

FROM node:10 as frontend_builder

WORKDIR /app

COPY ./frontend/package*.json ./

RUN npm install .

COPY ./frontend .

RUN npm run build

FROM golang:1.18beta1 as backend_builder

WORKDIR /app

COPY ./backend/go.mod ./
COPY ./backend/go.sum ./

RUN go mod download

COPY ./backend ./

RUN go build -o tune ./cmd

FROM ubuntu:jammy-20211122
ARG APP=/usr/src/app

RUN apt-get update \
    && apt-get install -y ca-certificates tzdata \
    && apt-get upgrade -y

EXPOSE 8080

ENV TZ=Etc/UTC \
    APP_USER=appuser

RUN groupadd $APP_USER \
    && useradd -g $APP_USER $APP_USER \
    && mkdir -p ${APP}

COPY --from=backend_builder /app/tune ${APP}/tune
COPY --from=frontend_builder /app/dist ${APP}/website

RUN chown -R $APP_USER:$APP_USER ${APP}

USER $APP_USER
WORKDIR ${APP}

ENV DATABASE_PATH=${APP}/database
ENV GIN_MODE=release

CMD ["./tune", "--website-file-path", "./website"]

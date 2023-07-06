FROM nginx:1.21

WORKDIR /app
COPY output output

# Configure the nginx inside the docker image
COPY .templates/nginx.conf /etc/nginx/conf.d/


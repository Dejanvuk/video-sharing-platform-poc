package com.dejanvuk.microservices.videomicroservice.services;

import com.dejanvuk.microservices.api.comment.Comment;
import com.dejanvuk.microservices.videomicroservice.mappers.VideoMapper;
import com.dejanvuk.microservices.videomicroservice.payload.VideoPayload;
import com.dejanvuk.microservices.videomicroservice.persistence.VideoEntity;
import com.dejanvuk.microservices.videomicroservice.persistence.VideoRepository;
import com.dejanvuk.microservices.api.video.Video;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Service
public class VideoService implements IVideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    VideoRepository videoRepository;

    ReactiveMongoTemplate reactiveMongoTemplate;

    VideoMapper videoMapper;

    @Value("${app.COMMENT_SERVICE_MAX_TIMEOUT}")
    int COMMENT_SERVICE_MAX_TIMEOUT;

    @Autowired
    public void setVideoRepository(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Autowired
    public void setReactiveMongoTemplate(ReactiveMongoTemplate reactiveMongoTemplate) {
        this.reactiveMongoTemplate = reactiveMongoTemplate;
    }

    @Autowired
    public void setVideoMapper(VideoMapper videoMapper) {
        this.videoMapper = videoMapper;
    }

    @Value("${app.COMMENT_SERVICE_URL}")
    private String COMMENT_SERVICE_URL;

    public VideoService() {
    }

    @Override
    public void create(VideoPayload videoPayload) {
        VideoEntity videoEntity = new VideoEntity();
        videoEntity.setOwnerId(videoPayload.getOwnerId());
        videoEntity.setLocationUrl(videoPayload.getLocationUrl());
        videoEntity.setTitle(videoPayload.getTitle());
        videoEntity.setDescription(videoPayload.getDescription());
        videoEntity.setTags(videoPayload.getTags());
        videoEntity.setLikes(0);
        videoEntity.setDislikes(0);

        videoRepository.save(videoEntity).log().block();
    }

    @Override
    public Flux<Video> getOwnerVideos(String ownerId) {
        return videoRepository.findAllByOwnerId(ownerId).log().log()
                .map(videoEntity -> videoMapper.videoEntityToApi(videoEntity));
    }

    @Override
    public Flux<Video> getVideos() {
        return videoRepository.findAll().map(videoEntity -> videoMapper.videoEntityToApi(videoEntity));
    }

    @Override
    public void deleteVideo(String videoId) {
        videoRepository.deleteById(videoId).log().block();
    }

    @Override
    public void updateVideo(String videoId, VideoPayload videoPayload) {
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(videoId));
        Update update = new Update();
        if(!videoPayload.getTitle().isEmpty())
            update.set("title", videoPayload.getTitle());

        if(!videoPayload.getDescription().isEmpty())
            update.set("description", videoPayload.getDescription());

        reactiveMongoTemplate.updateFirst(query, update, VideoEntity.class).log().block();
    }

    @Retry(name = "getVideoComments")
    @CircuitBreaker(name = "getVideoComments")
    @Override
    public Flux<Comment> getVideoComments(String videoId) {
        WebClient webClient = WebClient.create(COMMENT_SERVICE_URL);
        return webClient.get().uri("/comments/video/" + videoId).retrieve().bodyToFlux(Comment.class).log().timeout(Duration.ofSeconds(COMMENT_SERVICE_MAX_TIMEOUT));
    }

}

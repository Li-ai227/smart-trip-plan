package com.smart.trip.server.tag.controller;

import com.smart.trip.common.dto.TagResponse;
import com.smart.trip.server.tag.service.TagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标签控制器（C-014）。
 *
 * <p>提供标签查询接口，用于前端偏好标签选择组件。</p>
 *
 * @author smart-trip-plan
 */
@RestController
@RequestMapping("/api/v1/trip-plan")
public class TagController {

    private static final Logger logger = LoggerFactory.getLogger(TagController.class);

    private final TagService tagService;

    /**
     * 构造函数注入依赖。
     *
     * @param tagService 标签服务
     */
    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 获取所有可用标签列表（Top 30，按频次降序）。
     *
     * <p>首次请求时会从 knowledge-base.json 提取并缓存至 Redis，后续请求直接返回缓存。</p>
     *
     * @return 标签响应，包含最多 30 个标签
     */
    @GetMapping("/tags")
    public ResponseEntity<TagResponse> getTags() {
        try {
            logger.debug("Fetching tags");
            TagResponse response = tagService.getAllTags();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to fetch tags", e);
            return ResponseEntity.ok(TagResponse.empty());
        }
    }
}

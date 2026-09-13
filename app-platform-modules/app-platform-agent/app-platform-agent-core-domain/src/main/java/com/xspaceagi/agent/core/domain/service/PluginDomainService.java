package com.xspaceagi.agent.core.domain.service;

import com.xspaceagi.agent.core.adapter.repository.entity.PluginConfig;

import java.util.List;

/**
 * 插件领域服务，包含配置、发布、统计数据相关接口
 */
public interface PluginDomainService {

    /**
     * 新增插件
     *
     * @param pluginConfig
     */
    void add(PluginConfig pluginConfig);

    /**
     * 删除插件
     *
     * @param pluginId
     */
    void delete(Long pluginId);

    /**
     * 根据空间ID删除插件
     *
     * @param spaceId
     */
    void deleteBySpaceId(Long spaceId);

    /**
     * 更新插件
     *
     * @param pluginConfig
     */
    void update(PluginConfig pluginConfig);

    PluginConfig queryById(Long pluginId);

    List<PluginConfig> queryListByIds(List<Long> pluginIds);

    /**
     * 根据空间ID查询插件列表
     */
    List<PluginConfig> queryListBySpaceId(Long spaceId);

    /**
     * 根据空间ID查询插件列表（不查 config 列，供不需要插件配置内容的列表场景使用，
     * 可避免大字段传输；返回实体的 config 为 null）
     */
    List<PluginConfig> queryListBySpaceIdWithoutConfig(Long spaceId);


    /**
     * 复制
     *
     * @param userId
     * @param pluginId
     * @return
     */
    Long copy(Long userId, Long pluginId);
}

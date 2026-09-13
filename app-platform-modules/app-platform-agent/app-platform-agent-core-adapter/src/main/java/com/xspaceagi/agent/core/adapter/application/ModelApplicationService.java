package com.xspaceagi.agent.core.adapter.application;

import com.xspaceagi.agent.core.adapter.dto.CodeCheckResultDto;
import com.xspaceagi.agent.core.adapter.dto.ModelQueryDto;
import com.xspaceagi.agent.core.adapter.dto.SortUpdateDTO;
import com.xspaceagi.agent.core.adapter.dto.config.ModelConfigDto;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;

public interface ModelApplicationService {

    /**
     * 添加或更新模型配置
     *
     * @param modelDto
     */
    void addOrUpdate(ModelConfigDto modelDto);


    /**
     * 更新模型访问控制状态
     *
     * @param id
     * @param status
     */
    void updateAccessControlStatus(Long id, Integer status);

    /**
     * 删除模型配置
     *
     * @param modelId
     */
    void delete(Long modelId);

    /**
     * 查询空间下的模型列表
     */
    List<ModelConfigDto> queryModelConfigLisBySpaceId(Long spaceId);

    /**
     * 查询可使用的模型列表
     *
     * @return
     */
    List<ModelConfigDto> queryModelConfigList(ModelQueryDto modelQueryDto);

    /**
     * 查询可使用的模型列表
     *
     * @param withCreator 是否补全创建人信息（false 时不查询用户表，且 creator/creatorId 置空，
     *                    供返回前本就不展示创建人的调用方使用，如会话可选模型列表）
     */
    List<ModelConfigDto> queryModelConfigList(ModelQueryDto modelQueryDto, boolean withCreator);

    /**
     * 获取商家全局模型
     *
     * @return
     */
    List<ModelConfigDto> queryTenantModelConfigList(Integer accessControlStatus);

    /**
     * 根据ID查询模型配置
     *
     * @param modelId
     * @return
     */
    ModelConfigDto queryModelConfigById(Long modelId);

    /**
     * 根据ID查询模型配置
     *
     * @param withCreator 是否补全创建人信息（false 时不查询用户表，且 creator/creatorId/apiInfoList 置空，
     *                    供返回前本就不展示创建人的调用方使用，如会话可选模型列表）
     */
    ModelConfigDto queryModelConfigById(Long modelId, boolean withCreator);

    List<ModelConfigDto> queryModelConfigListByIds(List<Long> modelIds);

    ModelConfigDto queryDefaultModelConfig();

    ModelConfigDto queryDefaultModelConfig(Long tenantId);

    /**
     * 检查模型使用权限
     *
     * @param modelId
     */
    void checkModelUsePermission(Long modelId);

    /**
     * 检查模型管理权限
     *
     * @param modelId
     */
    void checkModelManagePermission(Long modelId);


    /**
     * 通过提示词给到模型执行，返回结果根据实际定义的Bean
     *
     * @param sysPrompt  系统提示词
     * @param userPrompt 用户输入
     * @param type       类型
     * @return
     */
    <T> T call(String sysPrompt, String userPrompt, ParameterizedTypeReference<T> type);

    /**
     * 通过提示词给到模型执行，返回结果根据实际定义的Bean
     *
     * @param modelId    模型ID
     * @param sysPrompt  系统提示词
     * @param userPrompt 用户输入
     * @param type       类型
     * @return
     */
    <T> T call(Long modelId, String sysPrompt, String userPrompt, ParameterizedTypeReference<T> type);

    /**
     * 通过提示词给到模型执行，返回结果为定义的Bean
     *
     * @param userPrompt 用户输入
     * @param type       类型
     * @return
     */
    <T> T call(String userPrompt, ParameterizedTypeReference<T> type);

    /**
     * 通过提示词给到模型执行，返回结果为文本
     *
     * @param userPrompt 用户输入
     * @return
     */
    String call(String userPrompt);

    /**
     * 向量化
     *
     * @param texts
     * @return
     */
    List<float[]> embeddings(List<String> texts, Long modelId);

    ModelConfigDto getDefaultEmbedModel();

    void checkUserModelPermission(Long userId, Long modelId);

    CodeCheckResultDto codeSaleCheck(String code);

    /**
     * 测试模型连通性
     *
     * @param modelConfig
     * @param testPrompt
     * @return
     */
    String testModelConnectivity(ModelConfigDto modelConfig, String testPrompt);

    List<ModelConfigDto> getMySystemModels(Long userId, String tab);

    void updateModelSort(List<SortUpdateDTO> updateDTOS);
}

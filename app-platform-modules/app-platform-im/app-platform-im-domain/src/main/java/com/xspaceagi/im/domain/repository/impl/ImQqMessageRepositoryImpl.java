package com.xspaceagi.im.domain.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xspaceagi.im.domain.repository.ImQqMessageRepository;
import com.xspaceagi.im.infra.dao.enitity.ImQqMessage;
import com.xspaceagi.im.infra.dao.mapper.ImQqMessageMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ImQqMessageRepositoryImpl extends ServiceImpl<ImQqMessageMapper, ImQqMessage> implements ImQqMessageRepository {
}

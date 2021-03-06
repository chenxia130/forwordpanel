package com.leeroy.forwordpanel.forwordpanel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.leeroy.forwordpanel.forwordpanel.common.response.ApiResponse;
import com.leeroy.forwordpanel.forwordpanel.common.util.BeanCopyUtil;
import com.leeroy.forwordpanel.forwordpanel.dao.PortDao;
import com.leeroy.forwordpanel.forwordpanel.dao.ServerDao;
import com.leeroy.forwordpanel.forwordpanel.dao.UserPortDao;
import com.leeroy.forwordpanel.forwordpanel.dto.UserPortDTO;
import com.leeroy.forwordpanel.forwordpanel.dto.UserPortPageRequest;
import com.leeroy.forwordpanel.forwordpanel.model.Port;
import com.leeroy.forwordpanel.forwordpanel.model.Server;
import com.leeroy.forwordpanel.forwordpanel.model.UserPort;
import com.leeroy.forwordpanel.forwordpanel.model.UserPortForward;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class UserPortService {

    @Autowired
    private UserPortDao userPortDao;

    @Autowired
    private PortDao portDao;

    @Autowired
    private ServerDao serverDao;

    @Autowired
    private UserPortForwardService userPortForwardService;

    @Autowired
    private ForwardService forwardService;

    /**
     * 给用户分配端口
     * @param userPortList
     */
    public ApiResponse save(List<UserPort> userPortList) {
        for (UserPort userPort : userPortList) {
            if (userPort.getId() == null) {
                if (findByUserIdAndPort(userPort.getUserId(), userPort.getPortId()) != null) {
                    Port port = portDao.selectById(userPort.getPortId());
                    return ApiResponse.error("401","端口:"+port.getLocalPort()+"已被分配");
                }
                userPort.setDeleted(false);
                userPort.setDisabled(false);
                userPort.setCreateTime(new Date());
                userPortDao.insert(userPort);
            } else {
                userPort.setUpdateTime(new Date());
                userPortDao.updateById(userPort);
            }
            //创建中转记录
            userPortForwardService.createUserPortForward(userPort.getServerId(), userPort.getPortId(), userPort.getUserId());
        }
        return ApiResponse.ok();
    }


    /**
     * 查询用户端口
     * @param userId
     * @return
     */
    public List<UserPortDTO> findUserPortList(Integer userId) {
        LambdaQueryWrapper<UserPort> queryWrapper = Wrappers.<UserPort>lambdaQuery().eq(UserPort::getUserId, userId)
                .eq(UserPort::getDeleted, false);
        List<UserPort> userPorts = userPortDao.selectList(queryWrapper);
        List<UserPortDTO> userPortDTOList = BeanCopyUtil.copyListProperties(userPorts, UserPortDTO::new);
        for (UserPortDTO userPort : userPortDTOList) {
            Port port = portDao.selectById(userPort.getPortId());
            if(port!=null){
                userPort.setLocalPort(port.getLocalPort());
            }
            Server server = serverDao.selectById(userPort.getServerId());
            if(server!=null){
                userPort.setServerName(server.getServerName());
                userPort.setServerHost(server.getHost());
            }
        }
        return userPortDTOList;
    }

    /**
     * 查询用户端口
     * @param pageRequest
     * @return
     */
    public PageInfo<UserPortDTO> findUserPortList(UserPortPageRequest pageRequest) {
        LambdaQueryWrapper<UserPort> queryWrapper = Wrappers.<UserPort>lambdaQuery().eq(UserPort::getUserId, pageRequest.getUserId())
                .eq(UserPort::getDeleted, false).orderByDesc(UserPort::getCreateTime);
        Page<UserPortDTO> page = PageHelper.startPage(pageRequest.getPageNum(), pageRequest.getPageSize());
        List<UserPort> userPorts = userPortDao.selectList(queryWrapper);
        List<UserPortDTO> userPortDTOList = BeanCopyUtil.copyListProperties(userPorts, UserPortDTO::new);
        for (UserPortDTO userPort : userPortDTOList) {
            Port port = portDao.selectById(userPort.getPortId());
            if (port != null) {
                userPort.setLocalPort(port.getLocalPort());
            }
            Server server = serverDao.selectById(userPort.getServerId());
            if (server != null) {
                userPort.setServerName(server.getServerName());
                userPort.setServerHost(server.getHost());
            }
        }
        PageInfo<UserPortDTO> pageInfo = page.toPageInfo();
        pageInfo.setList(userPortDTOList);
        return pageInfo;
    }

    /**
     * 删除用户端口
     */
    public void delUserPort(Integer id){
        UserPort existPort = userPortDao.selectById(id);
        UserPort userPort = new UserPort();
        userPort.setId(id);
        userPort.setDeleted(true);
        userPortDao.updateById(userPort);
        //删除中转记录
        userPortForwardService.deleteUserPortForward(existPort.getPortId(), existPort.getUserId());
    }

    /**
     * 查询用户端口
     * @param userId
     * @param portId
     * @return
     */
    public UserPort findByUserIdAndPort(Integer userId, Integer portId) {
        LambdaQueryWrapper<UserPort> queryWrapper = Wrappers.<UserPort>lambdaQuery().eq(UserPort::getPortId, portId)
                .eq(UserPort::getDeleted, false);
        return userPortDao.selectOne(queryWrapper);
    }

    /**
     * 禁用用户
     *
     * @param id
     * @return
     */
    public ApiResponse disablePort(Integer id) {
        UserPort existPort = userPortDao.selectById(id);
        UserPortForward userPortForward = new UserPortForward();
        userPortForward.setPortId(existPort.getPortId());
        userPortForward.setUserId(existPort.getUserId());
        userPortForwardService.stopForward(userPortForward);
        userPortDao.updateDisable(true, id);
        return ApiResponse.ok();
    }


    /**
     * 禁用用户所有端口
     *
     * @param userId
     * @return
     */
    public ApiResponse disableUserPort(Integer userId) {
        LambdaQueryWrapper<UserPort> queryWrapper = Wrappers.<UserPort>lambdaQuery().eq(UserPort::getUserId, userId)
                .eq(UserPort::getDeleted, false);
        List<UserPort> userPorts = userPortDao.selectList(queryWrapper);
        for (UserPort userPort : userPorts) {
            UserPort existPort = userPortDao.selectById(userPort.getId());
            UserPortForward userPortForward = new UserPortForward();
            userPortForward.setPortId(existPort.getPortId());
            userPortForward.setUserId(existPort.getUserId());
            userPortForwardService.stopForward(userPortForward);
            userPortDao.updateDisable(true, userPort.getId());
        }
        return ApiResponse.ok();
    }


    /**
     * 启用用户
     *
     * @param id
     * @return
     */
    public ApiResponse enablePort(Integer id) {
        userPortDao.updateDisable(false, id);
        return ApiResponse.ok();
    }

}

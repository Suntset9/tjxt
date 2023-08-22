package com.tianji.promotion.service.impl;

import cn.hutool.core.io.unit.DataUnit;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService couponScopeService;//优惠券的限定范围业务类
    private final IExchangeCodeService exchangeCodeService;//兑换码的业务类
    private final IUserCouponService userCouponService;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public void savaeCoupon(CouponFormDTO dto) {
        //1.dto转po  保存优惠卷 coupon表
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        this.save(coupon);

        //2.判断是否限定了范围  dto.specific  如果为false直接return
        if (!dto.getSpecific()){
            return;//说明没有限定优惠卷的使用返回
        }

        //3.如果dto.specific 为true 需要校验dto.scopes
        List<Long> scopes = dto.getScopes();
        if (CollUtils.isEmpty(scopes)){
            throw new BizIllegalException("分类id不能为空");
        }

        //4。保存优惠卷的限定范围 coupon_scope 批量新增
/*        List<CouponScope> csList = new ArrayList<>();
        for (Long scope : scopes) {
            CouponScope couponScope = new CouponScope();
            couponScope.setCouponId(coupon.getId());
            couponScope.setBizId(scope);
            couponScope.setType(1);
            csList.add(couponScope);
        }*/
        //stream流写法
        /*List<CouponScope> csList = scopes.stream().map(new Function<Long, CouponScope>() {
            @Override
            public CouponScope apply(Long aLong) {
                return new CouponScope().setCouponId(coupon.getId()).setBizId(aLong).setType(1);
            }
        }).collect(Collectors.toList());*/

        List<CouponScope> csList = scopes.stream().map(aLong -> new CouponScope().setCouponId(coupon.getId()).setBizId(aLong).setType(1)).collect(Collectors.toList());

        couponScopeService.saveBatch(csList);

    }


    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        //1.分页条件查询优惠卷表 coupon
        Page<Coupon> page = this.lambdaQuery()
                .eq(query.getType() != null, Coupon::getDiscountType, query.getType())
                .eq(query.getStatus() != null, Coupon::getStatus, query.getStatus())
                .like(StringUtils.isNotEmpty(query.getName()), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }

        //封装vo返回
        List<CouponPageVO> list = BeanUtils.copyList(records,CouponPageVO.class);
        return PageDTO.of(page,list);
    }

    @Override
    public void beginIssue(Long id, CouponIssueFormDTO dto) {
        log.debug("发放优惠卷任务执行  线程名：{}",Thread.currentThread().getName());
        //1.校验
        if (id == null || !id.equals(dto.getId())){
            throw new BadRequestException("非法参数");
        }
        //2.校验优惠卷id是否存在
        Coupon coupon = this.getById(id);
        if (coupon == null){
            throw new BadRequestException("优惠卷不存在");
        }

        //3.校验优惠卷状态  只有待发放和赞同状态才能发放
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE){
            throw new BizIllegalException("只有待发放和暂停中的优惠卷才能发放");
        }
        LocalDateTime now = LocalDateTime.now();
        //该变量代表优惠卷是否立刻发放
        boolean isBeginIssue = dto.getIssueBeginTime() == null || !dto.getTermBeginTime().isAfter(now);
        //4.修改优惠卷的 领取时间 和 结束日期  使用有效期开始和结束日  天数  状态
        //写法一  逐个set属性
        /*if (isBeginIssue){
            coupon.setIssueBeginTime(now);
            coupon.setIssueEndTime(dto.getIssueEndTime());
            coupon.setStatus(CouponStatus.ISSUING);//如果时立刻发放 优惠卷状态需改为发放中
            //直接使用传递参数 没传则为null
            coupon.setTermDays(dto.getTermDays());
            coupon.setTermBeginTime(dto.getTermBeginTime());
            coupon.setTermEndTime(dto.getTermEndTime());
        }else {
            coupon.setIssueBeginTime(dto.getIssueBeginTime());
            coupon.setIssueEndTime(dto.getIssueEndTime());
            coupon.setStatus(CouponStatus.UN_ISSUE);//如果时立刻发放 优惠卷状态需改为发放中
            //直接使用传递参数 没传则为null
            coupon.setTermDays(dto.getTermDays());
            coupon.setTermBeginTime(dto.getTermBeginTime());
            coupon.setTermEndTime(dto.getTermEndTime());
        }
        this.updateById(coupon);*/

        //写法二： 拷贝方式
        Coupon tmp = BeanUtils.copyBean(dto, Coupon.class);
        if (isBeginIssue){
            tmp.setStatus(CouponStatus.ISSUING);
            tmp.setIssueBeginTime(now);
        }else {
            tmp.setStatus(CouponStatus.UN_ISSUE);
        }
        //写入数据库
        this.updateById(tmp);


        // 如果优惠券时立刻发放 将优惠券信息（优惠券id、领取券开始时间结束时间 发行总数量 限领数量） 采用Hash存入redis
        if (isBeginIssue){
            String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id; //prs:coupon:优惠券id
            //操作四次redis
            /*redisTemplate.opsForHash().put(key,"issueBeginTime",String.valueOf(DateUtils.toEpochMilli(now)));
            redisTemplate.opsForHash().put(key,"issueEndTime",String.valueOf(DateUtils.toEpochMilli(dto.getIssueEndTime())));
            redisTemplate.opsForHash().put(key,"totalNum",String.valueOf(coupon.getTotalNum()));
            redisTemplate.opsForHash().put(key,"userLimit",String.valueOf(coupon.getUserLimit()));*/

            //操作一次redis
            HashMap<String, String> map = new HashMap<>();
            map.put("issueBeginTime",String.valueOf(DateUtils.toEpochMilli(now)));
            map.put("issueEndTime",String.valueOf(DateUtils.toEpochMilli(dto.getIssueEndTime())));
            map.put("totalNum",String.valueOf(coupon.getTotalNum()));
            map.put("userLimit",String.valueOf(coupon.getUserLimit()));
            redisTemplate.opsForHash().putAll(key,map);
        }


        //5.如果优惠卷的领取方式为 指定发放 且 优惠卷之前的状态时待发放  需要生成兑换码
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus()==CouponStatus.DRAFT){
            //兑换码的截止时间， 就是优惠卷领取的截止时间 ，该时间封装到tmp中了
            coupon.setIssueEndTime(tmp.getIssueEndTime());
            exchangeCodeService.asyncGenerateExchangeCode(coupon);
        }
    }

    //查询正在发放中的优惠卷
    @Override
    public List<CouponVO> queryIssuingCoupons() {
        //1.查询db coupon 条件：发放中 手动领取
        List<Coupon> couponList = this.lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(couponList)){
            return CollUtils.emptyList();
        }
        //2.查询用户卷表user_coupon  条件当前用户  发放中的优惠id
        //正在发放中的优惠卷id集合
        Set<Long> couponIds = couponList.stream().map(coupon -> coupon.getId()).collect(Collectors.toSet());
        //当前用户 针对正在发放中的优惠券领取记录
        List<UserCoupon> list = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();

        /*Map<Long, Long> issueMap = new HashMap<>();//键: 优惠券id值:已领数量
        // 101  2
        // 102  1
        for (UserCoupon userCoupon : list) {
            Long num = issueMap.get(userCoupon.getCouponId());//优惠券领取数量
            if (num == null) {
                issueMap.put(userCoupon.getCouponId(), 1L); //第一次为空+1

            } else {
                issueMap.put(userCoupon.getCouponId(), Long.valueOf(num.intValue() + 1));//后续还有走这+1
            }
        }*/
        //3.统计当前用户 针对每一个券 的已领数量  map的键时优惠券的id  value时当前登录用户针对该券已领数量
        Map<Long, Long> issueMap = list
                .stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        //3.1.统计当前用户 针对每一个卷的 已领且未使用数量  map的键时优惠券的id  value时当前登录用户针对该券已领且未使用数量
        Map<Long, Long> unuseMap = list
                .stream()
                .filter(c->c.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        //4.po转vo返回
        List<CouponVO> volist = new ArrayList<>();
        for (Coupon c : couponList) {
            CouponVO vo = BeanUtils.copyBean(c, CouponVO.class);
            //优惠卷还有剩余 （issue_num（已发数量） < total_num（总数））  且 （统计用户卷表user_coupon取出当前用户已领数量 < user_limit(每个人限领)）
            Long issNum = issueMap.getOrDefault(c.getId(), 0L);//获取优惠券的已领数量
            boolean avaliable = c.getIssueNum() < c.getTotalNum() && issNum.intValue() < c.getUserLimit();
            vo.setAvailable(avaliable);//是否可以领取
            //统计用户券表 user_coupon取出当前用户已领且未使用的券数量
            boolean received = unuseMap.getOrDefault(c.getId(), 0L) > 0;
            vo.setReceived(received);//是否可以使用
            volist.add(vo);
        }

        return volist;
    }
}

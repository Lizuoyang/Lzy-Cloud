# Lzy-Cloud
SpringCloud微服务实战—搭建企业级开发框架-后端Demo


## 目录结构

![](./doc/img/目录结构和规范/应用分层.png)

- VO（View Object）：显示层对象，通常是 Web 向模板渲染引擎层传输的对象。
- DTO（Data Transfer Object）：数据传输对象，前端像后台进行传输的对象，类似于param。
- BO（Business Object）：业务对象，内部业务对象，只在内部传递，不对外进行传递。
- Model：模型层，此对象与数据库表结构一一对应，通过 Mapper 层向上传输数据源对象。
- Controller：主要是对外部访问控制进行转发，各类基本参数校验，或者不复用的业务简单处理等。为了简单起见，一些与事务无关的代码也在这里编写。
- FeignClient：由于微服务之间存在互相调用，这里是内部请求的接口。
- Controller：主要是对内部访问控制进行转发，各类基本参数校验，或者不复用的业务简单处理等。为了简单起见，一些与事务无关的代码也在这里编写。
- Service 层：相对具体的业务逻辑服务层。 
- Manager 层：通用业务处理层，它有如下特征：
  -  1） 对第三方平台封装的层，预处理返回结果及转化异常信息，适配上层接口。
  -  2） 对 Service 层通用能力的下沉，如缓存方案、中间件通用处理。
  -  3） 与 DAO 层交互，对多个 DAO 的组合复用。
- Mapper持久层：数据访问层，与底层 MySQL进行数据交互。 
- Task层：由于每个服务之间会存在定时任务，比如定时确认收货，定时将活动失效等情况，这里面的Task实际上连接的是`xxl-job`（具体可以查看 https://github.com/xuxueli/xxl-job ）进行任务调度。
- Listener：监听 `RocketMQ` 进行处理，有时候会监听`easyexcel`相关数据。

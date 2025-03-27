package org.example.framework;

import org.example.annotation.Autowirte;
import org.example.annotation.Component;
//import org.example.annotation.Configuration;
import org.example.annotation.Service;

import javax.annotation.*;
import java.io.File;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//二级缓存的实现
public class Applicatio2 {

    private Map<String, Object> singletonObjects = new ConcurrentHashMap<>();

    private Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>();

    private Map<Class<?>, List<String>> allBeanNamesByType = new ConcurrentHashMap<>();


    public static Applicatio2 start(Class aClass) throws Exception {
        return new Applicatio2().run(aClass);
    }

    private Applicatio2 run(Class aClass) throws Exception {
        String packageName = aClass.getPackage().getName().replace(".", "/");
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        URL resource = contextClassLoader.getResource(packageName);
        String path = resource.getPath();
        path = path.substring(0, path.indexOf("/class"));
        List<Class> classList = getClassNameByFile(path);
        constructor(classList.stream().filter(e -> e.isAnnotationPresent(Service.class)).collect(Collectors.toList()));
        serviceInit();
        return this;
    }


    public <T> T getBean(String beanName) throws Exception {
        return (T) getBeanCache(beanName, null);
    }

    public <T> T getBean(Class<T> aClass) throws Exception {
        String beanName = transformedBeanName(aClass);
        T bean = getBean(beanName);
        if (Objects.nonNull(bean)) {
            return bean;
        }
        List<String> list = allBeanNamesByType.get(aClass);
        if (Objects.isNull(list)) {
            return null;
        }
        if (list.size() != 1) {
            throw new RuntimeException("找到多个bean");
        }
        return getBean(allBeanNamesByType.get(aClass).iterator().next());
    }

    private Object getBeanCache(String name, Class clazz) throws Exception {
        Object bean = getBeanCache(name);
        if (Objects.nonNull(bean)) {
            return bean;
        }
        return getBeanCache(clazz);
    }

    private Object getBeanCache(Class clazz) throws Exception {
        Object bean = getBeanCache(clazz.getSimpleName());
        if (Objects.nonNull(bean)) {
            return bean;
        }
        List<String> list = allBeanNamesByType.get(clazz);
        if (Objects.isNull(list)) {
            return null;
        }
        if (list.size() != 1) {
            throw new RuntimeException("找到多个Bean:" + clazz.getName());
        }
        return getBeanCache(allBeanNamesByType.get(clazz).iterator().next());
    }

    private Object getBeanCache(String name) throws Exception {
        if (singletonObjects.containsKey(name)) {
            return singletonObjects.get(name);
        }
        if(earlySingletonObjects.containsKey(name)){
            return earlySingletonObjects.get(name);
        }
        return null;
    }


    public void serviceInit() throws Exception {
        if (earlySingletonObjects.isEmpty()) {
            return;
        }
        for (Object serviceBean : earlySingletonObjects.values()) {
            if (autowirteInjection(serviceBean)) {
                singletonObjects.put(transformedBeanName(serviceBean.getClass()), serviceBean);
                earlySingletonObjects.remove(transformedBeanName(serviceBean.getClass()));
            }
        }
        if (!earlySingletonObjects.isEmpty()) {
            serviceInit();
        }
    }

    public boolean autowirteInjection(Object bean) throws Exception {
        List<Field> fieldList = Stream.of(Arrays.asList(bean.getClass().getFields()), Arrays.asList(bean.getClass().getDeclaredFields()))
                .flatMap(Collection::stream)
                .filter(e -> e.isAnnotationPresent(Autowirte.class))
                .distinct()
                .collect(Collectors.toList());
        List<Method> methodList = Stream.of(Arrays.asList(bean.getClass().getMethods()), Arrays.asList(bean.getClass().getDeclaredMethods()))
                .flatMap(Collection::stream)
                .filter(e -> e.isAnnotationPresent(Autowirte.class))
                .distinct()
                .collect(Collectors.toList());
        for (Field field : fieldList) {
            Object paramBean = getBeanCache(field.getName(), field.getType());
            if (Objects.isNull(paramBean)) {
                return false;
            }
            field.setAccessible(true);
            field.set(bean, paramBean);
        }
        for (Method method : methodList) {
            List<Object> params = new ArrayList<>();
            Parameter[] parameters = method.getParameters();
            for (Parameter parameter : parameters) {
                Object parameterBean = getBeanCache(parameter.getName(), parameter.getType());
                if (Objects.isNull(parameterBean)) {
                    return false;
                }
                params.add(parameterBean);
            }
            method.setAccessible(true);
            method.invoke(bean, params.toArray());
        }
        return true;
    }

    public String transformedBeanName(Class aclass) {
        if (aclass.isAnnotationPresent(Service.class)) {
            Service service = (Service) aclass.getAnnotation(Service.class);
            if (Objects.nonNull(service.value()) && !service.value().isEmpty()) {
                return service.value();
            }
        }
        return aclass.getSimpleName();
    }


    public void constructor(List<Class> classList) throws Exception {
        List<Class> resultClass = new ArrayList<>();
//        List<Object> beans = new ArrayList<>();
        for (Class classes : classList) {
            Constructor defaultConstructor = null;
            Constructor autowriteConstructor = null;
            Set<Constructor> constructorSet = new HashSet<>();
            constructorSet.addAll(Arrays.asList(classes.getConstructors()));
            constructorSet.addAll(Arrays.asList(classes.getDeclaredConstructors()));
            if (constructorSet.size() == 1) {
                defaultConstructor = constructorSet.iterator().next();
            }
            List<Constructor> constructorList = constructorSet.stream().filter(e -> e.isAnnotationPresent(Autowirte.class)).collect(Collectors.toList());
            if (constructorList.size() > 1) {
                throw new RuntimeException("存在多个autowrite" + classes.getSimpleName());
            }
            if (constructorList.size() == 1) {
                autowriteConstructor = constructorList.get(0);
            }
            Constructor constructor = null;

            if (Objects.nonNull(autowriteConstructor)) {
                constructor = autowriteConstructor;
            } else if (Objects.nonNull(defaultConstructor)) {
                constructor = defaultConstructor;
            }
            Object objectBean = null;
            if (Objects.isNull(constructor)) {
                objectBean = classes.newInstance();
            } else {
                List<Object> params = new ArrayList<>();
                boolean flag = true;
                for (Parameter parameter : constructor.getParameters()) {
                    Object bean = getBeanCache(transformedBeanName(parameter.getType()), parameter.getType());
                    if (Objects.isNull(bean)) {
                        flag = false;
                        break;
                    }
                    params.add(bean);
                }
                if (flag) {
                    constructor.setAccessible(true);
                    objectBean = constructor.newInstance(params.toArray());
                }
            }
            if (Objects.nonNull(objectBean)) {
                earlySingletonObjects.put(transformedBeanName(classes), objectBean);
                Class[] interfaces = classes.getInterfaces();
                for (Class anInterface : interfaces) {
                    if (!allBeanNamesByType.containsKey(anInterface.getName())) {
                        allBeanNamesByType.put(anInterface, Arrays.asList(classes.getSimpleName()));
                    } else {
                        allBeanNamesByType.get(anInterface).add(classes.getSimpleName());
                    }
                }
            } else {
                resultClass.add(classes);
            }
        }
        if (!resultClass.isEmpty()) {
            constructor(resultClass);
        }
    }


    public List<Class> getClassNameByFile(String path) throws ClassNotFoundException {
        List<Class> classList = new ArrayList<>();
        File file = new File(path);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (File listFile : file.listFiles()) {
            if (listFile.isDirectory()) {
                classList.addAll(getClassNameByFile(listFile.getPath()));
            } else {
                String childFilePath = listFile.getPath();
                if (childFilePath.endsWith(".class")) {
                    childFilePath = childFilePath.substring(childFilePath.indexOf("\\classes") + 9, childFilePath.lastIndexOf("."));
                    childFilePath = childFilePath.replace("\\", ".");
                    Class aClass = classLoader.loadClass(childFilePath);
                    if (!getAnnotation(aClass) || aClass.isAnnotation()) {
                        continue;
                    }
                    classList.add(aClass);
                }
            }
        }
        return classList;
    }

    public boolean getAnnotation(Class<?> clazz) {
        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == Deprecated.class ||
                    annotation.annotationType() == SuppressWarnings.class ||
                    annotation.annotationType() == Override.class ||
                    annotation.annotationType() == PostConstruct.class ||
                    annotation.annotationType() == PreDestroy.class ||
                    annotation.annotationType() == Resource.class ||
                    annotation.annotationType() == Resources.class ||
                    annotation.annotationType() == Generated.class ||
                    annotation.annotationType() == Target.class ||
                    annotation.annotationType() == Retention.class ||
                    annotation.annotationType() == Documented.class ||
                    annotation.annotationType() == Inherited.class) {
                continue;
            }
            if (annotation.annotationType() == Component.class) {
                return true;
            } else {
                return getAnnotation(annotation.annotationType());
            }
        }
        return false;
    }
}

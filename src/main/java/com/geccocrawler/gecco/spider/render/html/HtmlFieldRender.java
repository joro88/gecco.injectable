package com.geccocrawler.gecco.spider.render.html;

import com.geccocrawler.gecco.GeccoContext;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.reflections.ReflectionUtils;

import com.geccocrawler.gecco.annotation.HtmlField;
import com.geccocrawler.gecco.request.HttpRequest;
import com.geccocrawler.gecco.response.HttpResponse;
import com.geccocrawler.gecco.spider.SpiderBean;
import com.geccocrawler.gecco.spider.render.FieldRender;
import com.geccocrawler.gecco.spider.render.FieldRenderException;
import com.geccocrawler.gecco.utils.ReflectUtils;

import net.sf.cglib.beans.BeanMap;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class HtmlFieldRender implements FieldRender {
    protected GeccoContext context;

    public HtmlFieldRender(GeccoContext context) {
        this.context = context;
    }
    
	@Override
	public void render(HttpRequest request, HttpResponse response, BeanMap beanMap, SpiderBean bean) {
		Map<String, Object> fieldMap = new HashMap<String, Object>();
		Set<Field> htmlFields = ReflectionUtils.getAllFields(bean.getClass(), ReflectionUtils.withAnnotation(HtmlField.class));
		for (Field htmlField : htmlFields) {
			Object value = injectHtmlField(request, response, htmlField, bean.getClass());
			if(value != null) {
				fieldMapSet(fieldMap, htmlField, value, beanMap, bean);
			}
		}
		beanMap.putAll(fieldMap); // ATTENTION: if some properties are not added, maybe they dont have getter and setter methods !
	}

    protected void fieldMapSet(Map<String, Object> fieldMap, Field htmlField, Object value, BeanMap beanMap, SpiderBean bean) {
        fieldMap.put(htmlField.getName(), value);
    }

	protected Object injectHtmlField(HttpRequest request, HttpResponse response, Field field,	Class<? extends SpiderBean> clazz) {
		HtmlField htmlField = field.getAnnotation(HtmlField.class);
		String content = response.getContent();
		HtmlParser parser = context.getFactory().createHtmlParser(request.getUrl(), content, this, field);
		// parser.setLogClass(clazz);
		String cssPath = htmlField.cssPath();
		Class<?> type = field.getType();// 属性的类
		boolean isArray = type.isArray();// 是否是数组类型
		boolean isList = ReflectUtils.haveSuperType(type, List.class);// 是List类型
		if (isList) {
			Type genericType = field.getGenericType();// 获得包含泛型的类型
			Class genericClass = ReflectUtils.getGenericClass(genericType, 0);// 泛型类
			if (ReflectUtils.haveSuperType(genericClass, SpiderBean.class)) {
				// List<spiderBean>
				return parser.$beanList(cssPath, request, genericClass);
			} else {
				// List<Object>
				try {
					return parser.$basicList(cssPath, field);
				} catch (Exception ex) {
					processRenderFieldfException(field, content, ex);
				}
			}
		} else if (isArray) {
			Class genericClass = type.getComponentType();
			if (ReflectUtils.haveSuperType(genericClass, SpiderBean.class)) {
				List<SpiderBean> list = parser.$beanList(cssPath, request, genericClass);
				SpiderBean[] a = (SpiderBean[]) Array.newInstance(genericClass, list.size());
				return list.toArray(a);
			} else {
				// List<Object>
				try {
					return parser.$basicList(cssPath, field).toArray();
				} catch (Exception ex) {
					processRenderFieldfException(field, content, ex);
				}
			}
		} else {
			if (ReflectUtils.haveSuperType(type, SpiderBean.class)) {
				// SpiderBean
				return parser.$bean(cssPath, request, (Class<? extends SpiderBean>) type);
			} else {
				// Object
				try {
					return parser.$basic(cssPath, field);
				} catch (Exception ex) {
					processRenderFieldfException(field, content, ex);
				}
			}
		}
		return null;
	}

    public void processRenderFieldfException(Field field, String content, Exception ex) {
        //throw new FieldRenderException(field, content, ex);
        FieldRenderException.log(field, content, ex);
    }

}
